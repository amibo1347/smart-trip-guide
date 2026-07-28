package com.travel.planner.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * OpenStreetMap Overpass API 로 반경 내 장소(POI)를 카테고리별로 가져온다.
 * 키가 필요 없고 전 세계를 다루므로 국내·해외를 같은 코드로 처리한다.
 *
 * <p>Overpass 는 공용 무료 인스턴스라 느리고 간헐적으로 실패한다 → 실패 시 빈 목록으로 폴백하고,
 * 결과는 상위 서비스에서 캐시한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OverpassClient {

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final String UA = "smart-travel-planner/1.0 (personal travel planner)";
    private static final int TIMEOUT_MS = 25_000;

    private final ObjectMapper objectMapper;

    /**
     * 중심 좌표 반경 안의 해당 카테고리 장소들.
     *
     * @param radiusMeters 반경(m)
     * @param limit        최대 개수
     */
    public List<DiscoveredPlace> findNearby(BigDecimal lat, BigDecimal lon,
                                            PlaceCategory category, int radiusMeters, int limit) {
        String latS = lat.setScale(6, RoundingMode.HALF_UP).toPlainString();
        String lonS = lon.setScale(6, RoundingMode.HALF_UP).toPlainString();
        String query = """
                [out:json][timeout:25];
                (
                %s);
                out center %d;
                """.formatted(category.toOverpassClause(radiusMeters, latS, lonS), limit);

        try {
            String json = Jsoup.connect(ENDPOINT)
                    .userAgent(UA)
                    .header("Accept", "application/json")
                    .requestBody("data=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method(org.jsoup.Connection.Method.POST)
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .timeout(TIMEOUT_MS)
                    .execute().body();
            return parse(objectMapper.readTree(json), category);
        } catch (Exception e) {
            log.debug("Overpass 조회 실패 ({} {},{}): {}", category, latS, lonS, e.toString());
            return List.of();
        }
    }

    private List<DiscoveredPlace> parse(JsonNode root, PlaceCategory category) {
        List<DiscoveredPlace> out = new ArrayList<>();
        JsonNode elements = root.path("elements");
        if (!elements.isArray()) {
            return out;
        }
        for (JsonNode el : elements) {
            JsonNode tags = el.path("tags");
            String name = text(tags, "name:ko", "name");
            if (name == null) {
                continue;
            }
            // node 는 lat/lon, way 는 out center 로 내려오는 center.lat/lon
            BigDecimal lat = coord(el, "lat");
            BigDecimal lon = coord(el, "lon");
            if (lat == null) {
                lat = coord(el.path("center"), "lat");
                lon = coord(el.path("center"), "lon");
            }
            if (lat == null || lon == null) {
                continue;
            }
            out.add(new DiscoveredPlace(
                    el.path("type").asText("node") + "/" + el.path("id").asLong(),
                    name,
                    category,
                    text(tags, "tourism", "amenity", "shop", "historic"),
                    lat, lon,
                    addressOf(tags),
                    null,   // 사진 — 폴백 경로에서 위키백과로 채운다
                    null,   // 평점 — OSM 에는 없음
                    0,      // 리뷰 수 — 폴백에서는 위키 조회수를 대신 넣는다
                    null)); // 거리 — 호출측에서 계산
        }
        return out;
    }

    /** OSM addr:* 태그를 사람이 읽는 한 줄 주소로. 없으면 null. */
    private static String addressOf(JsonNode tags) {
        String full = text(tags, "addr:full");
        if (full != null) {
            return full;
        }
        StringBuilder sb = new StringBuilder();
        for (String key : new String[]{"addr:province", "addr:city", "addr:district", "addr:street", "addr:housenumber"}) {
            String v = text(tags, key);
            if (v != null) {
                sb.append(sb.isEmpty() ? "" : " ").append(v);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static BigDecimal coord(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : new BigDecimal(n.asText()).setScale(7, RoundingMode.HALF_UP);
    }

    /** 주어진 키들 중 처음으로 값이 있는 것. */
    private static String text(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode n = node.get(k);
            if (n != null && !n.isNull() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }
}
