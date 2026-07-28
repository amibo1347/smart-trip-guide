package com.travel.planner.discovery;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 탐색: 목적지의 <b>인기 장소</b>와 선택 장소 <b>주변 장소(카테고리별)</b>를 만든다.
 *
 * <p>목록을 코드에 적어 넣지 않는다 — 전부 외부 데이터로 만든다.
 * <ol>
 *   <li>기본: Google Places (사진·평점·리뷰수·정확한 주소, 인기순 정렬을 그대로 제공)</li>
 *   <li>키가 없으면: OSM(Overpass) + 위키백과로 폴백 — 품질은 낮지만 앱은 계속 동작한다</li>
 * </ol>
 * 어느 쪽이든 화면에는 같은 모양({@link DiscoveredPlace})으로 나간다.
 */
@Service
@RequiredArgsConstructor
public class PlaceDiscoveryService {

    /** 폴백 경로에서 인기도를 조회할 상위 후보 수(외부 호출 절약). */
    private static final int FALLBACK_LOOKUPS = 20;

    private final GooglePlacesClient google;
    private final OverpassClient overpass;
    private final WikipediaClient wikipedia;

    /** 목적지·반경 단위 결과 캐시(같은 화면 재진입 시 외부 호출 반복 방지). */
    private final Map<String, List<DiscoveredPlace>> cache = new ConcurrentHashMap<>();

    /** 지금 어떤 소스를 쓰는지 — 화면에 출처/품질 안내를 띄우기 위해 노출. */
    public boolean usingGoogle() {
        return google.isEnabled();
    }

    /** 이름으로 장소 검색(목적지 고르기, 상단 검색창). */
    public List<DiscoveredPlace> search(String query, String lang, int limit) {
        if (google.isEnabled()) {
            return withPhotos(google.searchText(query, lang, limit));
        }
        return List.of(); // 폴백 경로의 검색은 기존 /api/places/search(Nominatim)가 담당
    }

    /**
     * 목적지 주변 '인기 장소' 상위 N.
     * Google 경로는 리뷰 수 기준 인기순, 폴백 경로는 위키백과 조회수 기준.
     */
    public List<DiscoveredPlace> popular(BigDecimal lat, BigDecimal lon, int radiusKm, int limit, String lang) {
        String key = "pop:%s:%s:%d:%s:%s".formatted(round(lat), round(lon), radiusKm, lang, usingGoogle());
        List<DiscoveredPlace> cached = cache.get(key);
        if (cached != null) {
            return cached.stream().limit(limit).toList();
        }

        List<DiscoveredPlace> result = google.isEnabled()
                ? withPhotos(google.searchNearby(lat, lon, PlaceCategory.SIGHT, radiusKm * 1000, limit, lang))
                : popularFallback(lat, lon, radiusKm, limit, lang);

        cache.put(key, result);
        return result.stream().limit(limit).toList();
    }

    /**
     * 기준 장소 주변의 카테고리별 장소(가까운 순, 거리 표기).
     *
     * @param radiusKm 반경(km) — 화면 기본값 10km
     */
    public List<DiscoveredPlace> nearby(BigDecimal lat, BigDecimal lon, PlaceCategory category,
                                        int radiusKm, int limit, String lang) {
        String key = "near:%s:%s:%s:%d:%s:%s".formatted(round(lat), round(lon), category, radiusKm, lang, usingGoogle());
        List<DiscoveredPlace> cached = cache.get(key);
        if (cached == null) {
            List<DiscoveredPlace> raw = google.isEnabled()
                    ? google.searchNearby(lat, lon, category, radiusKm * 1000, limit, lang)
                    : dedupe(overpass.findNearby(lat, lon, category, radiusKm * 1000, limit * 2));
            cached = withPhotos(raw).stream()
                    .map(p -> p.withDistance(distanceKm(lat, lon, p.latitude(), p.longitude())))
                    .sorted(Comparator.comparingDouble(p -> p.distanceKm() == null ? Double.MAX_VALUE : p.distanceKm()))
                    .toList();
            cache.put(key, cached);
        }
        return cached.stream().limit(limit).toList();
    }

    /**
     * 사진 참조를 화면에서 바로 쓸 수 있는 URL 로 바꾼다.
     * Google 경로는 API 키를 프런트에 노출하지 않으려고 우리 서버 프록시 주소로 돌린다.
     */
    private List<DiscoveredPlace> withPhotos(List<DiscoveredPlace> places) {
        if (!google.isEnabled()) {
            return places;
        }
        List<DiscoveredPlace> out = new ArrayList<>();
        for (DiscoveredPlace p : places) {
            String ref = p.photoUrl(); // 이 시점엔 photos[0].name 이 들어 있다
            out.add(ref == null ? p
                    : p.withPhoto("/api/discovery/photo?ref=" + java.net.URLEncoder.encode(ref,
                            java.nio.charset.StandardCharsets.UTF_8)));
        }
        return out;
    }

    /** 키가 없을 때: OSM 관광 POI + 위키백과 조회수로 인기순을 흉내 낸다. */
    private List<DiscoveredPlace> popularFallback(BigDecimal lat, BigDecimal lon, int radiusKm, int limit, String lang) {
        List<DiscoveredPlace> candidates = dedupe(
                overpass.findNearby(lat, lon, PlaceCategory.SIGHT, radiusKm * 1000, 40));
        List<DiscoveredPlace> scored = new ArrayList<>();
        int lookups = 0;
        for (DiscoveredPlace p : candidates) {
            long views = 0;
            String photo = null;
            if (lookups++ < FALLBACK_LOOKUPS) {
                views = wikipedia.popularity(null, p.name(), lang);
                if (views > 0) {
                    photo = wikipedia.photoUrl(null, p.name(), lang);
                }
            }
            scored.add(new DiscoveredPlace(p.providerId(), p.name(), p.category(), p.subtype(),
                    p.latitude(), p.longitude(), p.address(), photo, null, views, null));
        }
        scored.sort(Comparator.comparingLong(DiscoveredPlace::reviewCount).reversed());
        return scored;
    }

    /** 이름 기준 중복 제거(같은 장소가 node/way 로 두 번 잡히는 경우). */
    private static List<DiscoveredPlace> dedupe(List<DiscoveredPlace> places) {
        Map<String, DiscoveredPlace> byName = new LinkedHashMap<>();
        for (DiscoveredPlace p : places) {
            byName.putIfAbsent(p.name(), p);
        }
        return new ArrayList<>(byName.values());
    }

    /** 두 좌표 사이 거리(km). 하버사인. */
    static double distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return Math.round(r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10) / 10.0;
    }

    private static String round(BigDecimal v) {
        return v.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
