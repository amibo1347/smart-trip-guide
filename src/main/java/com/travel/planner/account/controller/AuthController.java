package com.travel.planner.account.controller;

import com.travel.planner.account.dto.UserResponse;
import com.travel.planner.account.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 로그인 상태 조회. 프론트가 진입 시 호출해 세션 로그인 여부를 확인한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    public record AuthStatus(boolean authenticated, UserResponse user) {
    }

    @GetMapping("/me")
    public AuthStatus me(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return new AuthStatus(false, null);
        }
        String email = principal.getAttribute("email");
        if (email == null) {
            return new AuthStatus(false, null);
        }
        return new AuthStatus(true, userService.getByEmail(email));
    }
}
