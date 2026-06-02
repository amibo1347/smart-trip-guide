package com.travel.planner.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationRequest(
        @NotBlank String clientUuid,        // 멱등키 (클라이언트 생성 UUID)
        @NotNull LocalDateTime recordedAt,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        Float accuracyM
) {
}
