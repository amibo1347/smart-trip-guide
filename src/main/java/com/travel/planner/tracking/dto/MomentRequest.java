package com.travel.planner.tracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 통합 기록 입력(사진 없는 경로 — 오프라인 큐 JSON 재전송 포함).
 * 위치·기분·금액·메모는 모두 선택. 사진은 multipart 경로에서만.
 * 좌표/금액은 범위 검증 — 잘못된 값이 합계·외부 지오코딩으로 흘러가지 않도록.
 */
public record MomentRequest(
        @NotBlank String clientUuid,
        @NotNull LocalDateTime recordedAt,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        Float accuracyM,
        @Size(max = 16) String mood,
        @PositiveOrZero @DecimalMax("100000000") BigDecimal amount,
        @Size(max = 30) String category,
        @Size(max = 200) String memo
) {
}
