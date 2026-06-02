package com.travel.planner.config;

import com.travel.planner.account.oauth.CustomOAuth2UserService;
import com.travel.planner.account.oauth.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * 보안 설정.
 *
 * <p>핵심: OAuth2 로그인은 <b>소셜 자격증명(ClientRegistration)이 있을 때만</b> 활성화된다.
 * 자격증명이 없으면(=oauth 프로필 미적용) ClientRegistrationRepository 빈이 없으므로
 * oauth2Login 설정을 건너뛰고, 앱은 지금까지처럼 그대로 동작한다.
 *
 * <p>현재는 모든 요청을 permitAll 로 열어 둔다(기존 흐름 유지). 인증 강제는 추후 단계에서 적용.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 세션 기반 로그인 컨텍스트 저장소(로컬 로그인 + 소셜 로그인 공용). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2LoginSuccessHandler successHandler,
            SecurityContextRepository securityContextRepository) throws Exception {

        http
                // JSON API + 세션 쿠키. CSRF는 1차에서 비활성(추후 필요 시 /api 한정 토큰 적용).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(c -> c.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        // 공개 API: 헬스/로그인 상태/로그인/회원가입
                        .requestMatchers("/api/health", "/api/auth/me", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        // 그 외 모든 /api/** 는 로그인 필요
                        .requestMatchers("/api/**").authenticated()
                        // 정적 리소스, /, /oauth2/**, /login/**, /logout 등은 공개
                        .anyRequest().permitAll())
                // 미인증 /api 요청은 리다이렉트 대신 401 (JSON 클라이언트용)
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpServletResponse.SC_OK)));

        // 소셜 자격증명이 설정된 경우에만 OAuth2 로그인 활성화
        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                    .successHandler(successHandler));
        }

        return http.build();
    }
}
