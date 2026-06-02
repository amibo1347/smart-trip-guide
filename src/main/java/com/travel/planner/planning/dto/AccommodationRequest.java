package com.travel.planner.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AccommodationRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @PositiveOrZero BigDecimal cost,
        String address
) {
}
