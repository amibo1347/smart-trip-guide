package com.travel.planner.share;

import com.travel.planner.account.security.CurrentUser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final CurrentUser currentUser;

    /** 공유 켜기 — 토큰 발급(소유자). { token, path } 반환. */
    @PostMapping("/api/trips/{tripId}/share")
    public Map<String, String> enable(@PathVariable Long tripId, Authentication auth) {
        String token = shareService.enable(tripId, currentUser.requireId(auth));
        return Map.of("token", token, "path", "/share/" + token);
    }

    /** 공유 끄기 — 토큰 폐기(소유자). */
    @DeleteMapping("/api/trips/{tripId}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable Long tripId, Authentication auth) {
        shareService.disable(tripId, currentUser.requireId(auth));
    }

    /** 읽기 전용 공유 일정 조회 — 비로그인 허용(SecurityConfig 에서 permitAll). */
    @GetMapping("/api/shared/{token}")
    public SharedTripResponse view(@PathVariable String token) {
        return shareService.getByToken(token);
    }
}
