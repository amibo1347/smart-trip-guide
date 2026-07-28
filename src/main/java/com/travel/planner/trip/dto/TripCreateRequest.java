package com.travel.planner.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 여행 생성 요청. (인증 도입 전까지 userId를 명시적으로 전달)
 */
public record TripCreateRequest(
        @NotBlank @Size(max = 100) String title,
        // 목적지(선택) — 넣으면 인기/주변 장소 탐색이 그 좌표 기준으로 켜진다.
        @Size(max = 120) String destinationName,
        BigDecimal destinationLat,
        BigDecimal destinationLng,
        @Size(max = 1000) String destinationPhoto,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Positive Integer headcount,
        @PositiveOrZero BigDecimal budgetLimit,
        @Size(max = 50) String concept
) {
}
