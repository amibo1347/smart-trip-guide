package com.travel.planner.currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 목적지 통화 + 원화(KRW) 대비 환율 제공.
 * <p>환율은 무료·키 불필요 API(open.er-api.com, base=KRW)에서 가져와 24시간 캐시하고,
 * 호출 실패 시 내장 근사 환율표로 폴백한다(앱이 환율 때문에 죽지 않도록).
 * 모든 금액은 원화로 저장되며, 여기서 주는 perKrw(=목적지통화/1KRW)로 외화 환산만 표시한다.
 */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);
    private static final String API = "https://open.er-api.com/v6/latest/KRW";
    private static final long TTL_MS = 24 * 60 * 60 * 1000L; // 24h

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicReference<Cache> cache = new AtomicReference<>();

    /** 목적지 문자열(도시/국가명)에 맞는 통화 환산 정보. */
    public CurrencyInfo forDestination(String destination) {
        String code = resolveCode(destination);
        if ("KRW".equals(code)) {
            return new CurrencyInfo("KRW", "₩", BigDecimal.ONE, "기준 통화", true);
        }
        Cache c = currentRates();
        BigDecimal perKrw = c.rates().get(code);
        boolean live = c.live() && perKrw != null;
        if (perKrw == null) {
            perKrw = FALLBACK.get(code);
        }
        if (perKrw == null) {
            // 통화는 알지만 환율을 못 구함 → 환산 불가, 통화만 표기
            return new CurrencyInfo(code, symbol(code), null, "환율 정보 없음", false);
        }
        return new CurrencyInfo(code, symbol(code), perKrw, c.asOf(), live);
    }

    // ---- 환율 조회 + 24h 캐시 (실패 시 폴백) ----
    private Cache currentRates() {
        Cache c = cache.get();
        long now = System.currentTimeMillis();
        if (c != null && (now - c.fetchedAt()) < TTL_MS && c.live()) {
            return c;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) {
                JsonNode root = objectMapper.readTree(res.body());
                if ("success".equals(root.path("result").asText()) && root.path("rates").isObject()) {
                    var map = new java.util.HashMap<String, BigDecimal>();
                    root.path("rates").fields().forEachRemaining(e -> {
                        if (e.getValue().isNumber()) map.put(e.getKey(), e.getValue().decimalValue());
                    });
                    String asOf = root.path("time_last_update_utc").asText("");
                    Cache fresh = new Cache(Map.copyOf(map), now, asOf, true);
                    cache.set(fresh);
                    return fresh;
                }
            }
            log.warn("환율 API 응답 이상(status={})", res.statusCode());
        } catch (Exception e) {
            log.warn("환율 API 호출 실패 — 내장 환율표로 폴백: {}", e.getMessage());
        }
        // 폴백: 내장표(있으면 직전 캐시 유지, 없으면 FALLBACK)
        if (c != null) {
            return c; // 만료됐지만 직전 값이라도 사용
        }
        Cache fb = new Cache(FALLBACK, now, "내장 근사 환율", false);
        cache.set(fb);
        return fb;
    }

    /** 목적지 문자열에 포함된 키워드로 통화 코드 결정(없으면 KRW). */
    private static String resolveCode(String destination) {
        if (destination == null) return "KRW";
        String d = destination.replace(" ", "");
        for (Map.Entry<String, String> e : DEST_CCY.entrySet()) {
            if (d.contains(e.getKey())) return e.getValue();
        }
        return "KRW";
    }

    private static String symbol(String code) {
        return SYMBOLS.getOrDefault(code, code + " ");
    }

    private record Cache(Map<String, BigDecimal> rates, long fetchedAt, String asOf, boolean live) {}

    public record CurrencyInfo(String code, String symbol, BigDecimal perKrw, String asOf, boolean live) {}

    /** 도시/국가 키워드 → 통화코드. (부분일치) */
    private static final Map<String, String> DEST_CCY = Map.ofEntries(
            Map.entry("오사카", "JPY"), Map.entry("도쿄", "JPY"), Map.entry("동경", "JPY"),
            Map.entry("교토", "JPY"), Map.entry("후쿠오카", "JPY"), Map.entry("삿포로", "JPY"),
            Map.entry("오키나와", "JPY"), Map.entry("나고야", "JPY"), Map.entry("일본", "JPY"),
            Map.entry("방콕", "THB"), Map.entry("태국", "THB"), Map.entry("푸켓", "THB"),
            Map.entry("다낭", "VND"), Map.entry("하노이", "VND"), Map.entry("호치민", "VND"),
            Map.entry("나트랑", "VND"), Map.entry("푸꾸옥", "VND"), Map.entry("베트남", "VND"),
            Map.entry("싱가포르", "SGD"), Map.entry("홍콩", "HKD"),
            Map.entry("타이베이", "TWD"), Map.entry("대만", "TWD"),
            Map.entry("쿠알라룸푸르", "MYR"), Map.entry("말레이시아", "MYR"),
            Map.entry("발리", "IDR"), Map.entry("인도네시아", "IDR"), Map.entry("자카르타", "IDR"),
            Map.entry("세부", "PHP"), Map.entry("마닐라", "PHP"), Map.entry("보라카이", "PHP"), Map.entry("필리핀", "PHP"),
            Map.entry("괌", "USD"), Map.entry("사이판", "USD"), Map.entry("하와이", "USD"),
            Map.entry("뉴욕", "USD"), Map.entry("로스앤젤레스", "USD"), Map.entry("미국", "USD"),
            Map.entry("파리", "EUR"), Map.entry("로마", "EUR"), Map.entry("바르셀로나", "EUR"),
            Map.entry("프랑크푸르트", "EUR"), Map.entry("암스테르담", "EUR"), Map.entry("유럽", "EUR"),
            Map.entry("런던", "GBP"), Map.entry("영국", "GBP"),
            Map.entry("시드니", "AUD"), Map.entry("호주", "AUD"));

    private static final Map<String, String> SYMBOLS = Map.ofEntries(
            Map.entry("JPY", "¥"), Map.entry("USD", "$"), Map.entry("EUR", "€"), Map.entry("GBP", "£"),
            Map.entry("THB", "฿"), Map.entry("VND", "₫"), Map.entry("SGD", "S$"), Map.entry("HKD", "HK$"),
            Map.entry("TWD", "NT$"), Map.entry("MYR", "RM"), Map.entry("IDR", "Rp"), Map.entry("PHP", "₱"),
            Map.entry("AUD", "A$"), Map.entry("KRW", "₩"));

    /** 내장 근사 환율(목적지통화 / 1KRW). API 실패 시에만 사용. */
    private static final Map<String, BigDecimal> FALLBACK = Map.ofEntries(
            Map.entry("USD", new BigDecimal("0.00073")), Map.entry("JPY", new BigDecimal("0.110")),
            Map.entry("EUR", new BigDecimal("0.00068")), Map.entry("GBP", new BigDecimal("0.00057")),
            Map.entry("THB", new BigDecimal("0.0266")), Map.entry("VND", new BigDecimal("18.5")),
            Map.entry("SGD", new BigDecimal("0.00098")), Map.entry("HKD", new BigDecimal("0.0057")),
            Map.entry("TWD", new BigDecimal("0.0235")), Map.entry("MYR", new BigDecimal("0.00345")),
            Map.entry("IDR", new BigDecimal("11.8")), Map.entry("PHP", new BigDecimal("0.0425")),
            Map.entry("AUD", new BigDecimal("0.0011")));
}
