package com.travel.planner.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** 계획 항목의 실제 결과 입력(방문/비용/만족도). */
public record ActualUpsertRequest(
        boolean visited,
        @PositiveOrZero BigDecimal actualCost,
        @Min(1) @Max(5) Integer satisfaction
) {
}
