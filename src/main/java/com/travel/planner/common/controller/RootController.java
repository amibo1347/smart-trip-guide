package com.travel.planner.common.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 루트 경로 안내. 브라우저로 접속 시 404 대신 서비스/엔드포인트 정보를 보여준다.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "service", "smart-travel-planner",
                "status", "UP",
                "endpoints", List.of(
                        "GET  /api/health",
                        "POST /api/users",
                        "GET  /api/users/{id}",
                        "POST /api/trips",
                        "GET  /api/trips/{id}",
                        "GET  /api/trips?userId={userId}"
                )
        );
    }
}
