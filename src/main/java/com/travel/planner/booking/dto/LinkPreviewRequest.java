package com.travel.planner.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkPreviewRequest(
        @NotBlank String url
) {
}
