package com.travel.planner.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * SPA 라우트 포워딩. 공유 링크(/share/{token})로 '직접 접속'하면 정적 index.html 로 포워드해야
 * React 앱이 떠서 경로를 읽고 공유 뷰를 렌더한다(없으면 백엔드 정적 서빙이 404).
 */
@Controller
public class SpaController {

    @GetMapping("/share/{token}")
    public String share(@PathVariable String token) {
        return "forward:/index.html";
    }
}
