package com.travel.planner.config;

import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정 — React PWA에서 REST API 호출 허용.
 * 운영에선 프론트가 백엔드와 같은 도메인에서 서빙되지만, 브라우저가 비-GET 요청에 Origin 헤더를 붙이므로
 * 그 도메인도 허용해야 한다. 기본값에 로컬 개발 + Railway 도메인 패턴을 포함하고,
 * 다른 도메인이면 CORS_ALLOWED_ORIGINS(콤마 구분, 패턴 가능)로 오버라이드한다.
 * 기록 사진(/uploads/**)은 서버 로컬 파일 디렉터리에서 정적 서빙.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;
    private final String uploadDir;

    public WebConfig(@Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:8082,https://*.up.railway.app}") String origins,
                     @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.allowedOriginPatterns = origins.split(",");
        this.uploadDir = uploadDir;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // allowedOriginPatterns: 와일드카드(*.up.railway.app) 지원 + allowCredentials 와 함께 사용 가능.
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
