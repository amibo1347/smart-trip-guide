package com.travel.planner.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseRequest(
        @NotBlank String clientUuid,
        @NotNull @Positive BigDecimal amount,
        @Size(max = 30) String category,
        @Size(max = 100) String memo,
        @NotNull LocalDateTime spentAt
) {
}
