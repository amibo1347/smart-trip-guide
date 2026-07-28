package com.travel.planner.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

/**
 * 여행 날짜별 날씨. Open-Meteo(무료·키 불필요·전 세계) 사용.
 *
 * <p>예보 가능 범위는 오늘 기준 대략 [-90일 ~ +15일]뿐이다. 그 밖(먼 미래 여행)의 날짜는
 * 데이터가 없으므로 그냥 제외한다 — 프런트는 '예보 없음'으로 표시한다.
 * 목적지 좌표가 없는 여행은 빈 목록.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30분(예보는 자주 바뀌지 않음)

    private final TripService tripService;
    private final ObjectMapper objectMapper;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public List<DayWeather> forTrip(Long tripId, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        if (trip.getDestinationLat() == null || trip.getDestinationLng() == null
                || trip.getStartDate() == null || trip.getEndDate() == null) {
            return List.of();
        }

        // 예보 가능한 창으로 자른다. 벗어난 날짜(먼 미래)는 애초에 요청하지 않는다.
        LocalDate today = LocalDate.now();
        LocalDate start = max(trip.getStartDate(), today.minusDays(80));
        LocalDate end = min(trip.getEndDate(), today.plusDays(15));
        if (start.isAfter(end)) {
            return List.of();
        }

        String key = round(trip.getDestinationLat()) + "," + round(trip.getDestinationLng()) + ":" + start + ":" + end;
        Cached c = cache.get(key);
        if (c != null && System.currentTimeMillis() - c.at < CACHE_TTL_MS) {
            return c.data;
        }
        List<DayWeather> data = fetch(trip.getDestinationLat(), trip.getDestinationLng(), start, end);
        cache.put(key, new Cached(data, System.currentTimeMillis()));
        return data;
    }

    private List<DayWeather> fetch(BigDecimal lat, BigDecimal lng, LocalDate start, LocalDate end) {
        try {
            String url = ENDPOINT
                    + "?latitude=" + lat.setScale(4, RoundingMode.HALF_UP)
                    + "&longitude=" + lng.setScale(4, RoundingMode.HALF_UP)
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                    + "&timezone=auto&start_date=" + start + "&end_date=" + end;
            String json = Jsoup.connect(url)
                    .ignoreContentType(true).ignoreHttpErrors(true).maxBodySize(0).timeout(6000)
                    .execute().body();
            JsonNode daily = objectMapper.readTree(json).path("daily");
            JsonNode times = daily.path("time");
            if (!times.isArray()) {
                return List.of();
            }
            JsonNode codes = daily.path("weather_code");
            JsonNode maxs = daily.path("temperature_2m_max");
            JsonNode mins = daily.path("temperature_2m_min");
            JsonNode pops = daily.path("precipitation_probability_max");

            List<DayWeather> out = new ArrayList<>();
            for (int i = 0; i < times.size(); i++) {
                out.add(new DayWeather(
                        LocalDate.parse(times.get(i).asText()),
                        codes.path(i).asInt(0),
                        numOrNull(maxs, i),
                        numOrNull(mins, i),
                        pops.path(i).isNumber() ? pops.get(i).asInt() : null));
            }
            return out;
        } catch (Exception e) {
            log.debug("날씨 조회 실패: {}", e.toString());
            return List.of();
        }
    }

    private static Double numOrNull(JsonNode arr, int i) {
        JsonNode n = arr.path(i);
        return n.isNumber() ? n.asDouble() : null;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static String round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record Cached(List<DayWeather> data, long at) {
    }
}
