package com.travel.planner.account.service;

import com.travel.planner.account.dto.UserCreateRequest;
import com.travel.planner.account.dto.UserResponse;
import com.travel.planner.account.entity.AuthProvider;
import com.travel.planner.account.entity.User;
import com.travel.planner.account.repository.UserRepository;
import com.travel.planner.common.exception.DuplicateResourceException;
import com.travel.planner.common.exception.InvalidCredentialsException;
import com.travel.planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /** 로그인 실패 문구. 프론트가 이 원문을 키로 번역하므로 바꾸면 translations.js 도 함께 고쳐야 한다. */
    private static final String BAD_CREDENTIALS = "아이디 혹은 비밀번호가 틀렸습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.travel.planner.trip.repository.TripRepository tripRepository;

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("이미 사용 중인 이메일입니다: " + request.email());
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .provider(AuthProvider.LOCAL)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse get(Long id) {
        return UserResponse.from(getEntity(id));
    }

    /** 프로필 수정(닉네임·기본 출발지·아바타 프리셋). */
    @Transactional
    public UserResponse updateProfile(Long userId, String nickname, String defaultOrigin, String avatar) {
        User user = getEntity(userId);
        user.updateProfile(nickname, defaultOrigin, avatar);
        return UserResponse.from(user);
    }

    /** 아바타 사진 업로드(직접 올린 이미지). */
    @Transactional
    public UserResponse changeAvatar(Long userId, String uploadedUrl) {
        User user = getEntity(userId);
        user.changeAvatar(uploadedUrl);
        return UserResponse.from(user);
    }

    /** 비밀번호 변경(로컬 계정만). 현재 비밀번호가 맞아야 한다. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getEntity(userId);
        if (!user.isLocal() || user.getPasswordHash() == null) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("현재 비밀번호가 올바르지 않습니다.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    /** 회원 탈퇴 — 이 사용자의 여행(그리고 연쇄로 일정·기록·예약)까지 함께 삭제. */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = getEntity(userId);
        // 여행 삭제 시 FK ON DELETE CASCADE(V12 등)로 하위 데이터가 함께 지워진다.
        tripRepository.deleteAll(tripRepository.findByUserIdOrderByStartDateDesc(userId));
        userRepository.delete(user);
    }


    /**
     * 로컬(이메일/비밀번호) 로그인 검증. 성공 시 사용자 반환, 실패 시 401.
     */
    public User authenticateLocal(String email, String rawPassword) {
        // 계정 없음과 비밀번호 틀림을 같은 문구로 돌려준다 — 가입 여부가 새어 나가지 않게(계정 열거 방지).
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException(BAD_CREDENTIALS));
        if (user.getProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new InvalidCredentialsException(user.getProvider() + " 소셜 로그인으로 가입된 계정입니다. 소셜 버튼으로 로그인하세요.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException(BAD_CREDENTIALS);
        }
        return user;
    }

    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + email));
        return UserResponse.from(user);
    }

    /**
     * 소셜 로그인 사용자 조회/자동가입. provider+id 로 매칭, 없으면 동일 이메일 사용자에 연결, 그래도 없으면 신규.
     */
    @Transactional
    public User findOrCreateSocial(AuthProvider provider, String providerId, String email, String nickname) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .or(() -> (email != null) ? userRepository.findByEmail(email) : java.util.Optional.empty())
                .orElseGet(() -> userRepository.save(User.ofSocial(provider, providerId, email, nickname)));
    }

    /** 다른 도메인(Trip 등)에서 사용자 존재 검증용. */
    public User getEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + id));
    }
}
