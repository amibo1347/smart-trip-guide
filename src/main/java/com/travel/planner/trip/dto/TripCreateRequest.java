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
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Positive Integer headcount,
        @PositiveOrZero BigDecimal budgetLimit,
        @Size(max = 50) String concept
) {
}
