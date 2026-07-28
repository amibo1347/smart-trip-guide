package com.travel.planner.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 여행 회고 입력. budget_diff 는 서버가 계산. */
public record FeedbackUpsertRequest(
        @Min(1) @Max(5) Integer overallScore,
        @Size(max = 2000) String comment,
        @Size(max = 1000) String liked,
        @Size(max = 1000) String regret,
        @Size(max = 1000) String nextTime
) {
}
