package com.travel.planner.config;

import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정 — React PWA(별도 origin)에서 REST API 호출 허용.
 * 허용 origin은 CORS_ALLOWED_ORIGINS 환경변수로 오버라이드(콤마 구분).
 * 기록 사진(/uploads/**)은 서버 로컬 파일 디렉터리에서 정적 서빙.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String uploadDir;

    public WebConfig(@Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins,
                     @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.allowedOrigins = origins.split(",");
        this.uploadDir = uploadDir;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
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
