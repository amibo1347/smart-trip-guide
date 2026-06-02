package com.travel.planner.account.oauth;

import com.travel.planner.account.entity.AuthProvider;
import com.travel.planner.account.entity.User;
import com.travel.planner.account.service.UserService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 시 제공자별 프로필을 표준화하고, DB 사용자와 매칭/자동가입한다.
 * (Google / Kakao 응답 구조가 달라 각각 파싱)
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        Profile profile = switch (registrationId) {
            case "google" -> parseGoogle(attributes);
            case "kakao" -> parseKakao(attributes);
            default -> throw new OAuth2AuthenticationException("지원하지 않는 제공자: " + registrationId);
        };

        User user = userService.findOrCreateSocial(
                profile.provider(), profile.providerId(), profile.email(), profile.nickname());

        Map<String, Object> mapped = new HashMap<>();
        mapped.put("userId", user.getId());
        mapped.put("email", user.getEmail());
        mapped.put("nickname", user.getNickname());
        mapped.put("provider", user.getProvider().name());

        return new DefaultOAuth2User(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
                mapped,
                "email");
    }

    private Profile parseGoogle(Map<String, Object> attr) {
        String sub = (String) attr.get("sub");
        String email = (String) attr.get("email");
        String name = (String) attr.getOrDefault("name", email);
        return new Profile(AuthProvider.GOOGLE, sub, email, name);
    }

    @SuppressWarnings("unchecked")
    private Profile parseKakao(Map<String, Object> attr) {
        String providerId = String.valueOf(attr.get("id"));
        Map<String, Object> account = (Map<String, Object>) attr.getOrDefault("kakao_account", Map.of());
        Map<String, Object> properties = (Map<String, Object>) attr.getOrDefault("properties", Map.of());
        String email = (String) account.get("email"); // 동의 안 하면 null → 합성 이메일 대체
        String nickname = (String) properties.getOrDefault("nickname", "카카오사용자");
        if (email == null) {
            email = "kakao_" + providerId + "@social.local";
        }
        return new Profile(AuthProvider.KAKAO, providerId, email, nickname);
    }

    private record Profile(AuthProvider provider, String providerId, String email, String nickname) {
    }
}
