package com.travel.planner.weather;

import com.travel.planner.account.security.CurrentUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 날짜별 날씨. 목적지 좌표 + 여행 기간으로 Open-Meteo 조회.
 * 예보 범위(오늘±)를 벗어난 날짜는 목록에서 빠진다.
 */
@RestController
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final CurrentUser currentUser;

    @GetMapping("/api/trips/{tripId}/weather")
    public List<DayWeather> weather(@PathVariable Long tripId, Authentication auth) {
        return weatherService.forTrip(tripId, currentUser.requireId(auth));
    }
}
