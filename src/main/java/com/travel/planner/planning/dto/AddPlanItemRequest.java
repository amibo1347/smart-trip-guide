package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.PlanItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 일정 항목 추가 요청. place 는 선택(수동입력).
 */
public record AddPlanItemRequest(
        @NotNull PlanItemType type,
        @NotBlank @Size(max = 100) String title,
        LocalTime plannedStart,
        LocalTime plannedEnd,
        @PositiveOrZero BigDecimal estCost,
        @Valid PlaceInput place
) {
}
