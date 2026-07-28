package com.travel.planner.discovery;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 탐색 API — 일정을 직접 짤 때 쓰는 재료.
 * - GET /api/discovery/popular : 목적지 인기 장소 TOP N
 * - GET /api/discovery/nearby  : 선택 장소 주변(관광/식당·카페/쇼핑)
 * - GET /api/discovery/search  : 이름으로 장소 검색
 * - GET /api/discovery/photo   : 장소 사진 프록시(외부 API 키를 프런트에 노출하지 않기 위함)
 */
@RestController
@RequiredArgsConstructor
public class DiscoveryController {

    private final PlaceDiscoveryService discovery;
    private final GooglePlacesClient google;

    /**
     * 지금 어떤 소스를 쓰는지 + 오늘 남은 호출 수.
     * 화면 안내와, 상한에 걸려 품질이 떨어졌을 때 원인 파악에 쓴다.
     */
    public record DiscoveryStatus(boolean richData, int remainingToday) {
    }

    @GetMapping("/api/discovery/status")
    public DiscoveryStatus status() {
        return new DiscoveryStatus(discovery.usingGoogle(), google.remainingToday());
    }

    @GetMapping("/api/discovery/popular")
    public List<DiscoveredPlace> popular(@RequestParam BigDecimal lat,
                                         @RequestParam BigDecimal lng,
                                         @RequestParam(defaultValue = "15") int radiusKm,
                                         @RequestParam(defaultValue = "10") int limit,
                                         @RequestParam(defaultValue = "ko") String lang) {
        return discovery.popular(lat, lng, radiusKm, limit, lang);
    }

    @GetMapping("/api/discovery/nearby")
    public List<DiscoveredPlace> nearby(@RequestParam BigDecimal lat,
                                        @RequestParam BigDecimal lng,
                                        @RequestParam PlaceCategory category,
                                        @RequestParam(defaultValue = "10") int radiusKm,
                                        @RequestParam(defaultValue = "12") int limit,
                                        @RequestParam(defaultValue = "ko") String lang) {
        return discovery.nearby(lat, lng, category, radiusKm, limit, lang);
    }

    @GetMapping("/api/discovery/search")
    public List<DiscoveredPlace> search(@RequestParam("q") String query,
                                        @RequestParam(defaultValue = "10") int limit,
                                        @RequestParam(defaultValue = "ko") String lang) {
        return discovery.search(query, lang, limit);
    }

    /**
     * 사진 프록시. 프런트는 이 주소를 &lt;img src&gt; 로 쓰고, 실제 이미지 URL 해석(=API 키 사용)은 서버에서만 한다.
     * 해석된 URL 로 302 리다이렉트하므로 이미지 바이트가 우리 서버를 통과하지는 않는다.
     */
    @GetMapping("/api/discovery/photo")
    public ResponseEntity<Void> photo(@RequestParam("ref") String ref,
                                      @RequestParam(defaultValue = "480") int h) {
        String url = google.resolvePhoto(ref, h);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
