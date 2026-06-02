package com.travel.planner.trip.dto;

import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.entity.TripStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TripResponse(
        Long id,
        Long userId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int headcount,
        BigDecimal budgetLimit,
        String concept,
        TripStatus status,
        LocalDateTime createdAt
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getUserId(),
                trip.getTitle(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getHeadcount(),
                trip.getBudgetLimit(),
                trip.getConcept(),
                trip.getStatus(),
                trip.getCreatedAt()
        );
    }
}
