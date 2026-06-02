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
}
