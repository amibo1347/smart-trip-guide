package com.travel.planner.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record MoodRequest(
        @NotBlank String clientUuid,
        @NotBlank String emoji,
        @NotNull LocalDateTime recordedAt,
        Long locationLogId
) {
}
