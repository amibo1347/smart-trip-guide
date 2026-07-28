package com.travel.planner.account.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 (설계 2.1 Account).
 * LOCAL 가입은 password_hash 보관, 소셜(GOOGLE/KAKAO)은 provider_id 로 식별하고 비번 없음.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    /** 기본 출발지(AI 일정 생성의 출발지 기본값). 없으면 화면에서 빈칸/서울. */
    @Column(name = "default_origin", length = 50)
    private String defaultOrigin;

    /** 프로필 아바타. "preset:xxx"(기본 제공) 또는 "/uploads/.."(업로드). null 이면 기본 캐릭터. */
    @Column(length = 500)
    private String avatar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Builder
    private User(String email, String passwordHash, String nickname,
                 AuthProvider provider, String providerId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.provider = provider == null ? AuthProvider.LOCAL : provider;
        this.providerId = providerId;
    }

    /** 소셜 로그인 사용자 생성. */
    public static User ofSocial(AuthProvider provider, String providerId, String email, String nickname) {
        return User.builder()
                .provider(provider)
                .providerId(providerId)
                .email(email)
                .nickname(nickname)
                .build();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 프로필 수정(닉네임·기본 출발지·아바타). null 인 필드는 그대로 둔다. */
    public void updateProfile(String nickname, String defaultOrigin, String avatar) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname.trim();
        }
        if (defaultOrigin != null) {
            this.defaultOrigin = defaultOrigin.isBlank() ? null : defaultOrigin.trim();
        }
        if (avatar != null) {
            this.avatar = avatar.isBlank() ? null : avatar;
        }
    }

    /** 아바타 교체(업로드한 사진 URL). */
    public void changeAvatar(String avatar) {
        this.avatar = avatar;
    }

    /** 비밀번호 변경(로컬 계정). */
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public boolean isLocal() {
        return this.provider == AuthProvider.LOCAL;
    }
}
