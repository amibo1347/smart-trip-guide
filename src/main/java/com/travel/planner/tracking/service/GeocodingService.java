package com.travel.planner.tracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

/**
 * 좌표 → 사람이 읽는 짧은 지역명(예: "경기도 하남시 미사1동") 역지오코딩.
 * OpenStreetMap Nominatim 사용(키 불필요, 전 세계 지원). 실패/좌표 없음 시 null 반환 → 호출측은 좌표/생략으로 폴백.
 * Nominatim 사용 정책상 식별 가능한 User-Agent 를 보낸다(개인용, 저빈도 호출).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeocodingService {

    private static final String UA = "smart-travel-planner/1.0 (personal travel journal)";

    private final ObjectMapper objectMapper;

    /** 좌표를 행정구역 위주의 짧은 지역명으로 변환. 좌표 없음/범위 밖/변환 실패 시 null. */
    public String reverse(BigDecimal lat, BigDecimal lon) {
        if (!isValidCoord(lat, BigDecimal.valueOf(90)) || !isValidCoord(lon, BigDecimal.valueOf(180))) {
            return null;
        }
        try {
            // toPlainString: 과학적 표기(1E+2) 방지, 소수 6자리로 절단(불필요한 정밀도/URL 노이즈 제거)
            String latStr = lat.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String lonStr = lon.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2"
                    + "&lat=" + latStr + "&lon=" + lonStr + "&zoom=18&accept-language=ko";
            String json = Jsoup.connect(url)
                    .userAgent(UA)
                    .header("Accept", "application/json")
                    .ignoreContentType(true)
                    .timeout(5000)
                    .execute().body();
            return buildLabel(objectMapper.readTree(json).get("address"));
        } catch (Exception e) {
            log.debug("역지오코딩 실패 ({}, {}): {}", lat, lon, e.toString());
            return null;
        }
    }

    /** 값이 null 이 아니고 |값| <= limit 인지(위도 90 / 경도 180 범위 검증). */
    private static boolean isValidCoord(BigDecimal v, BigDecimal limit) {
        return v != null && v.abs().compareTo(limit) <= 0;
    }

    /** 광역→시군구→읍면동(국내) / state→city→suburb(해외) 중 최대 3단계를 공백으로 잇는다(중복 제거). */
    private static String buildLabel(JsonNode a) {
        if (a == null || a.isNull()) {
            return null;
        }
        String l1 = firstOf(a, "province", "state", "region", "city");
        String l2 = firstOf(a, "city", "county", "town", "municipality");
        String l3 = firstOf(a, "city_district", "borough", "suburb", "neighbourhood", "quarter", "village");
        List<String> parts = new ArrayList<>();
        for (String s : new String[]{l1, l2, l3}) {
            if (s != null && !s.isBlank() && !parts.contains(s)) {
                parts.add(s);
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static String firstOf(JsonNode a, String... keys) {
        for (String k : keys) {
            JsonNode n = a.get(k);
            if (n != null && !n.isNull() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }
}
