package com.travel.planner.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Google Places API (New) 클라이언트 — 장소의 사진·평점·리뷰수·정확한 주소를 가져온다.
 * '인기 장소'는 리뷰 수/평점이라는 실제 지표로 정렬되므로 목록을 코드에 적어 넣을 필요가 없다.
 *
 * <p>키(GOOGLE_PLACES_API_KEY)가 없으면 {@link #isEnabled()} 가 false 이고 모든 조회는 빈 목록을 준다
 * — 호출측(PlaceDiscoveryService)이 무료 소스로 폴백한다.
 */
@Component
@Slf4j
public class GooglePlacesClient {

    private static final String SEARCH_TEXT = "https://places.googleapis.com/v1/places:searchText";
    private static final String SEARCH_NEARBY = "https://places.googleapis.com/v1/places:searchNearby";
    private static final String PLACE_DETAILS = "https://places.googleapis.com/v1/places/";

    /**
     * 목록·탐색용 필드(Pro 등급). 평점/리뷰수를 <b>일부러 뺐다</b> —
     * 그 두 필드가 들어가면 Enterprise 등급이 되어 월 무료가 5,000 → 1,000건으로 줄기 때문.
     * 정렬은 rankPreference(요청 파라미터, 등급 무관)로 하므로 인기순은 그대로 유지된다.
     */
    private static final String LIST_FIELDS =
            "places.id,places.displayName,places.formattedAddress,places.location,"
            + "places.photos,places.primaryTypeDisplayName";

    /** 장소 한 곳을 자세히 볼 때만 쓰는 필드(평점·리뷰수 포함). 호출 빈도가 낮아 상위 등급이어도 부담이 적다. */
    private static final String DETAIL_FIELDS =
            "id,displayName,formattedAddress,location,photos,primaryTypeDisplayName,rating,userRatingCount";

    /** 카테고리 → Google 장소 타입. 화면의 3분류를 실제 타입으로 번역한다. */
    private static final Map<PlaceCategory, List<String>> TYPES = Map.of(
            PlaceCategory.SIGHT, List.of("tourist_attraction", "museum", "art_gallery", "park",
                    "historical_landmark", "zoo", "aquarium"),
            PlaceCategory.FOOD, List.of("restaurant", "cafe", "bakery", "bar"),
            PlaceCategory.SHOPPING, List.of("shopping_mall", "department_store", "clothing_store",
                    "market", "convenience_store"));

    private final String apiKey;
    private final int dailyLimit;
    private final ObjectMapper objectMapper;
    /** 사진 참조 → 실제 이미지 URL 캐시(사진 URL 해석에도 API 호출이 든다). */
    private final Map<String, String> photoCache = new ConcurrentHashMap<>();

    /** 하루 호출 수 — 청구 사고 방지용 자체 상한. 날짜가 바뀌면 0으로 되돌린다. */
    private final AtomicInteger todayCalls = new AtomicInteger();
    private volatile LocalDate counterDate = LocalDate.now();

    /**
     * 사진 할당량(429)에 걸리면 이 시각까지 사진 해석을 잠시 멈춘다.
     * 매 화면마다 12장씩 헛되이 Google 을 두드려 앱 카운터까지 태우는 낭비를 막는다.
     */
    private volatile long photoPausedUntil = 0L;
    private static final long PHOTO_PAUSE_MS = 5 * 60 * 1000L; // 429 후 5분 휴지

    public GooglePlacesClient(@Value("${google.places.api-key:}") String apiKey,
                              @Value("${google.places.daily-limit:300}") int dailyLimit,
                              ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.dailyLimit = dailyLimit;
        this.objectMapper = objectMapper;
    }

    /** 키가 있고 오늘 상한을 넘지 않았을 때만 사용. 상한을 넘으면 호출측이 무료 소스로 폴백한다. */
    public boolean isEnabled() {
        return !apiKey.isBlank() && remainingToday() > 0;
    }

    /** 오늘 남은 호출 가능 수(모니터링/화면 안내용). */
    public int remainingToday() {
        rollOverIfNewDay();
        return Math.max(0, dailyLimit - todayCalls.get());
    }

    private void rollOverIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) {
            synchronized (this) {
                if (!today.equals(counterDate)) {
                    counterDate = today;
                    todayCalls.set(0);
                }
            }
        }
    }

    /**
     * 호출 1건을 소비한다. 상한을 넘었으면 false — 이 경우 외부 요청을 보내지 않는다.
     * (Cloud Console 할당량과 별개로 앱 쪽에서도 막아 이중 안전장치를 만든다.)
     */
    private boolean consumeCall() {
        rollOverIfNewDay();
        if (todayCalls.incrementAndGet() > dailyLimit) {
            todayCalls.decrementAndGet();
            log.warn("Places 일일 호출 상한({}) 도달 — 무료 소스로 폴백합니다.", dailyLimit);
            return false;
        }
        return true;
    }

    /** 이름/키워드로 장소 검색(목적지 선택, 상단 검색창). */
    public List<DiscoveredPlace> searchText(String query, String lang, int limit) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        String body = """
                {"textQuery":%s,"languageCode":"%s","maxResultCount":%d}
                """.formatted(json(query), lang, Math.min(limit, 20));
        return post(SEARCH_TEXT, body, null);
    }

    /**
     * 반경 내 장소를 인기순(리뷰 많은 순)으로. 카테고리를 주면 그 타입만 본다.
     *
     * @param category null 이면 관광 중심(SIGHT)
     */
    public List<DiscoveredPlace> searchNearby(BigDecimal lat, BigDecimal lng, PlaceCategory category,
                                              int radiusMeters, int limit, String lang) {
        if (!isEnabled()) {
            return List.of();
        }
        PlaceCategory cat = category == null ? PlaceCategory.SIGHT : category;
        String types = TYPES.getOrDefault(cat, List.of()).stream()
                .map(GooglePlacesClient::json).reduce((a, b) -> a + "," + b).orElse("");
        String body = """
                {"includedTypes":[%s],"maxResultCount":%d,"rankPreference":"POPULARITY","languageCode":"%s",
                 "locationRestriction":{"circle":{"center":{"latitude":%s,"longitude":%s},"radius":%d}}}
                """.formatted(types, Math.min(limit, 20), lang,
                lat.setScale(6, RoundingMode.HALF_UP), lng.setScale(6, RoundingMode.HALF_UP),
                Math.min(radiusMeters, 50_000));
        return post(SEARCH_NEARBY, body, cat);
    }

    /**
     * 사진 참조(places/xxx/photos/yyy)를 실제 이미지 URL 로 바꾼다.
     * 키를 프런트에 노출하지 않으려고 서버에서만 해석한다.
     */
    public String resolvePhoto(String photoName, int maxHeight) {
        if (!isEnabled() || photoName == null || photoName.isBlank()) {
            return null;
        }
        // 성공한 것만 캐시에 남긴다. 실패(429/에러)는 캐시하지 않아 다음 기회에 다시 시도하도록 —
        // computeIfAbsent 로 빈 문자열을 캐시하면 그 사진은 영영 안 뜨게 되므로 별도로 처리한다.
        String cached = photoCache.get(photoName);
        if (cached != null) {
            return cached.isBlank() ? null : cached;
        }
        // 최근에 429 를 받았으면 잠시 아예 시도하지 않는다(불필요한 Google 재호출·카운터 소모 방지).
        if (System.currentTimeMillis() < photoPausedUntil || !consumeCall()) {
            return null;
        }
        try {
            String url = "https://places.googleapis.com/v1/" + photoName
                    + "/media?maxHeightPx=" + maxHeight + "&skipHttpRedirect=true&key=" + apiKey;
            org.jsoup.Connection.Response resp = Jsoup.connect(url)
                    .ignoreContentType(true).ignoreHttpErrors(true).maxBodySize(0).timeout(6000).execute();
            if (resp.statusCode() == 429) {
                photoPausedUntil = System.currentTimeMillis() + PHOTO_PAUSE_MS;
                log.debug("Places 사진 할당량 초과 — {}분간 사진 요청 중단", PHOTO_PAUSE_MS / 60000);
                return null; // 캐시하지 않음 → 휴지 후 다시 시도
            }
            String uri = objectMapper.readTree(resp.body()).path("photoUri").asText("");
            photoCache.put(photoName, uri);         // 성공/영구실패 모두 확정값이므로 캐시
            return uri.isBlank() ? null : uri;
        } catch (Exception e) {
            log.debug("Places 사진 해석 실패 ({}): {}", photoName, e.toString());
            return null;
        }
    }

    /**
     * 장소 한 곳의 상세(평점·리뷰수 포함). 일정에 담을 때처럼 '한 곳을 확정하는 순간'에만 호출한다.
     * 실패하거나 상한을 넘으면 null — 호출측은 평점 없이 진행한다.
     */
    public DiscoveredPlace details(String placeId, String lang) {
        if (!isEnabled() || placeId == null || placeId.isBlank() || !consumeCall()) {
            return null;
        }
        try {
            String res = Jsoup.connect(PLACE_DETAILS + placeId + "?languageCode=" + lang)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", DETAIL_FIELDS)
                    .ignoreContentType(true).ignoreHttpErrors(true).maxBodySize(0).timeout(10_000)
                    .execute().body();
            JsonNode p = objectMapper.readTree(res);
            if (p.has("error") || !p.has("displayName")) {
                return null;
            }
            List<DiscoveredPlace> one = parse(objectMapper.createArrayNode().add(p), null);
            return one.isEmpty() ? null : one.get(0);
        } catch (Exception e) {
            log.debug("Places 상세 조회 실패 ({}): {}", placeId, e.toString());
            return null;
        }
    }

    private List<DiscoveredPlace> post(String endpoint, String body, PlaceCategory category) {
        if (!consumeCall()) {
            return List.of(); // 일일 상한 초과 — 외부 요청을 아예 보내지 않는다
        }
        try {
            String res = Jsoup.connect(endpoint)
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", LIST_FIELDS)
                    .requestBody(body)
                    .method(Connection.Method.POST)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .maxBodySize(0)
                    .timeout(10_000)
                    .execute().body();
            JsonNode root = objectMapper.readTree(res);
            if (root.has("error")) {
                log.warn("Places API 오류: {}", root.path("error").path("message").asText());
                return List.of();
            }
            return parse(root.path("places"), category);
        } catch (Exception e) {
            log.debug("Places 호출 실패: {}", e.toString());
            return List.of();
        }
    }

    private List<DiscoveredPlace> parse(JsonNode places, PlaceCategory category) {
        List<DiscoveredPlace> out = new ArrayList<>();
        if (!places.isArray()) {
            return out;
        }
        for (JsonNode p : places) {
            String name = p.path("displayName").path("text").asText(null);
            JsonNode loc = p.path("location");
            if (name == null || loc.isMissingNode()) {
                continue;
            }
            // 사진은 첫 장만 — 목록 화면에 필요한 건 대표 이미지 하나다.
            JsonNode photos = p.path("photos");
            String photoRef = photos.isArray() && !photos.isEmpty() ? photos.get(0).path("name").asText(null) : null;

            out.add(new DiscoveredPlace(
                    p.path("id").asText(null),
                    name,
                    category,
                    p.path("primaryTypeDisplayName").path("text").asText(null),
                    BigDecimal.valueOf(loc.path("latitude").asDouble()).setScale(7, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(loc.path("longitude").asDouble()).setScale(7, RoundingMode.HALF_UP),
                    p.path("formattedAddress").asText(null),
                    photoRef,                                   // 해석은 서비스 단계에서(프록시 URL 로 치환)
                    p.has("rating") ? p.path("rating").asDouble() : null,
                    p.path("userRatingCount").asLong(0),
                    null));
        }
        return out;
    }

    /** JSON 문자열 리터럴로 안전하게 감싼다. */
    private static String json(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + '"';
    }
}
