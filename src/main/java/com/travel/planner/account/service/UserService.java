package com.travel.planner.account.service;

import com.travel.planner.account.dto.UserCreateRequest;
import com.travel.planner.account.dto.UserResponse;
import com.travel.planner.account.entity.AuthProvider;
import com.travel.planner.account.entity.User;
import com.travel.planner.account.repository.UserRepository;
import com.travel.planner.common.exception.DuplicateResourceException;
import com.travel.planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
