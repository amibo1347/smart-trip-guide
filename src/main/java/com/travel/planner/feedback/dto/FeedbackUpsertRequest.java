package com.travel.planner.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 여행 회고 입력. budget_diff 는 서버가 계산. */
public record FeedbackUpsertRequest(
        @Min(1) @Max(5) Integer overallScore,
        String comment
) {
}
