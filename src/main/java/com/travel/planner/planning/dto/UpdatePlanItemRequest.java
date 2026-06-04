package com.travel.planner.planning.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 일정 항목 수정 요청. 편집 폼이 보내는 값으로 제목/시간/예상비용을 갱신한다.
 * (시간·비용은 null 이면 해당 값 비움)
 */
public record UpdatePlanItemRequest(
        @Size(max = 100) String title,
        LocalTime plannedStart,
        LocalTime plannedEnd,
        @PositiveOrZero BigDecimal estCost
) {
}
