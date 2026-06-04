package com.travel.planner.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 통합 기록 입력(사진 없는 경로 — 오프라인 큐 JSON 재전송 포함).
 * 위치·기분·금액·메모는 모두 선택. 사진은 multipart 경로에서만.
 */
public record MomentRequest(
        @NotBlank String clientUuid,
        @NotNull LocalDateTime recordedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        Float accuracyM,
        String mood,
        BigDecimal amount,
        String category,
        String memo
) {
}
