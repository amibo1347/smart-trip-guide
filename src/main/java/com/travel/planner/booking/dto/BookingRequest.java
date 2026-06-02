package com.travel.planner.booking.dto;

import com.travel.planner.booking.entity.BookingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BookingRequest(
        @NotNull BookingType type,
        @NotBlank @Size(max = 200) String title,
        @PositiveOrZero BigDecimal price,
        @Size(max = 1000) String bookingUrl,
        @Size(max = 1000) String imageUrl,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 300) String memo
) {
}
