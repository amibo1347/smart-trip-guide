package com.travel.planner.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * 위키백과에서 장소의 <b>대표 사진</b>과 <b>인기도(월 조회수)</b>를 가져온다.
 * '인기 장소 TOP N' 순위를 목록을 직접 적어 넣지 않고 실제 데이터로 만들기 위한 근거.
 *
 * <p>둘 다 외부 호출이라 결과를 메모리에 캐시한다(장소 정보는 자주 변하지 않음).
 * 실패는 조용히 넘긴다 — 사진이 없거나 인기도가 0이어도 목록 자체는 보여줘야 하기 때문.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WikipediaClient {

    private static final String UA = "smart-travel-planner/1.0 (personal travel planner)";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM'0100'");

    /** 사진/조회수 캐시. 키는 "{lang}:{title}". 앱 수명 동안 유지(장소 정보는 거의 안 변함). */
    private final Map<String, String> photoCache = new ConcurrentHashMap<>();
    private final Map<String, Long> viewsCache = new ConcurrentHashMap<>();
    private static final String NONE = "\0"; // '조회했지만 없음'을 캐시에 남기기 위한 표식

    private final ObjectMapper objectMapper;

    /**
     * 대표 사진 URL. wikipediaTag 는 OSM 의 "ko:해운대해수욕장" 형태이거나 그냥 제목일 수 있다.
     * 없으면 null.
     */
    public String photoUrl(String wikipediaTag, String fallbackTitle, String lang) {
        Ref ref = Ref.parse(wikipediaTag, fallbackTitle, lang);
        if (ref == null) {
            return null;
        }
        String key = ref.key();
        String cached = photoCache.get(key);
        if (cached != null) {
            return NONE.equals(cached) ? null : cached;
        }
        String url = fetchPhoto(ref);
        photoCache.put(key, url == null ? NONE : url);
        return url;
    }

    /** 최근 2개월 월간 조회수 합. 문서가 없으면 0. */
    public long popularity(String wikipediaTag, String fallbackTitle, String lang) {
        Ref ref = Ref.parse(wikipediaTag, fallbackTitle, lang);
        if (ref == null) {
            return 0;
        }
        return viewsCache.computeIfAbsent(ref.key(), k -> fetchViews(ref));
    }

    private String fetchPhoto(Ref ref) {
        try {
            String url = "https://%s.wikipedia.org/w/api.php?action=query&format=json&prop=pageimages"
                    .formatted(ref.lang())
                    + "&piprop=thumbnail&pithumbsize=480&titles=" + enc(ref.title());
            JsonNode pages = objectMapper.readTree(get(url)).path("query").path("pages");
            for (JsonNode page : pages) {
                JsonNode src = page.path("thumbnail").get("source");
                if (src != null && !src.isNull()) {
                    return src.asText();
                }
            }
        } catch (Exception e) {
            log.debug("위키 사진 조회 실패 ({}): {}", ref.title(), e.toString());
        }
        return null;
    }

    private long fetchViews(Ref ref) {
        try {
            // 이번 달은 집계가 끝나지 않았으므로 지난 두 달을 본다.
            LocalDate now = LocalDate.now().withDayOfMonth(1);
            String from = now.minusMonths(2).format(MONTH);
            String to = now.format(MONTH);
            String url = "https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/"
                    + "%s.wikipedia/all-access/user/%s/monthly/%s/%s"
                    .formatted(ref.lang(), enc(ref.title()), from, to);
            JsonNode items = objectMapper.readTree(get(url)).path("items");
            long sum = 0;
            for (JsonNode item : items) {
                sum += item.path("views").asLong(0);
            }
            return sum;
        } catch (Exception e) {
            return 0; // 문서 없음(404) 포함 — 인기도 0
        }
    }

    private static String get(String url) throws java.io.IOException {
        return Jsoup.connect(url)
                .userAgent(UA)
                .header("Accept", "application/json")
                .ignoreContentType(true)
                .maxBodySize(0)
                .timeout(6000)
                .execute().body();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s.replace(' ', '_'), StandardCharsets.UTF_8);
    }

    /** 위키 문서 참조(언어 + 제목). OSM 의 "ko:제목" 형태를 풀어준다. */
    private record Ref(String lang, String title) {
        static Ref parse(String wikipediaTag, String fallbackTitle, String defaultLang) {
            if (wikipediaTag != null && !wikipediaTag.isBlank()) {
                int colon = wikipediaTag.indexOf(':');
                if (colon > 0 && colon <= 3) { // "ko:...", "en:..." 형태
                    return new Ref(wikipediaTag.substring(0, colon), wikipediaTag.substring(colon + 1));
                }
                return new Ref(defaultLang, wikipediaTag);
            }
            if (fallbackTitle != null && !fallbackTitle.isBlank()) {
                return new Ref(defaultLang, fallbackTitle);
            }
            return null;
        }

        String key() {
            return lang + ":" + title;
        }
    }
}
