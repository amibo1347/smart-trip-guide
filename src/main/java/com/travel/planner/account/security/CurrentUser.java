package com.travel.planner.account.security;

import com.travel.planner.account.entity.User;
import com.travel.planner.account.repository.UserRepository;
import com.travel.planner.common.exception.InvalidCredentialsException;
import com.travel.planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 현재 세션의 로그인 사용자를 해석한다(로컬/소셜 공통).
 * 컨트롤러는 클라이언트가 보낸 userId 를 신뢰하지 않고 이 값을 사용한다.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public User require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new InvalidCredentialsException("로그인이 필요합니다.");
        }
        String email = (authentication.getPrincipal() instanceof OAuth2User oauthUser)
                ? oauthUser.getAttribute("email")   // 소셜 로그인
                : authentication.getName();          // 로컬 로그인
        if (email == null) {
            throw new InvalidCredentialsException("로그인이 필요합니다.");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }

    public Long requireId(Authentication authentication) {
        return require(authentication).getId();
    }
}
