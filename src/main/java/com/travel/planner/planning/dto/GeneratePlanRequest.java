package com.travel.planner.planning.dto;

import jakarta.validation.constraints.Size;

/**
 * AI 일정 생성 요청. note: 지역/요청사항(선택).
 */
public record GeneratePlanRequest(
        @Size(max = 500) String note
) {
}
