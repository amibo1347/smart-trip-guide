package com.travel.planner.account.controller;

import com.travel.planner.account.dto.LoginRequest;
import com.travel.planner.account.dto.UserResponse;
import com.travel.planner.account.entity.User;
import com.travel.planner.account.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증: 로컬(이메일/비밀번호) 로그인 + 현재 로그인 상태 조회.
 * 소셜 로그인은 Spring Security OAuth2 가 /oauth2/** 로 처리하며, 결과는 동일한 세션/ /me 로 확인된다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository;

    public record AuthStatus(boolean authenticated, UserResponse user) {
    }

    @PostMapping("/login")
    public AuthStatus login(@Valid @RequestBody LoginRequest req,
                            HttpServletRequest request, HttpServletResponse response) {
        User user = userService.authenticateLocal(req.email(), req.password());
        establishSession(user.getEmail(), request, response);
        return new AuthStatus(true, UserResponse.from(user));
    }

    /** 세션에 인증 컨텍스트 저장 (이후 요청은 세션 쿠키로 식별). principal = 이메일. */
    private void establishSession(String email, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @GetMapping("/me")
    public AuthStatus me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new AuthStatus(false, null);
        }
        String email = (authentication.getPrincipal() instanceof OAuth2User oauthUser)
                ? oauthUser.getAttribute("email")   // 소셜 로그인
                : authentication.getName();          // 로컬 로그인(principal = email)
        if (email == null) {
            return new AuthStatus(false, null);
        }
        return new AuthStatus(true, userService.getByEmail(email));
    }
}
