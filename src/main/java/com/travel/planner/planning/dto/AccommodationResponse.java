package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.Accommodation;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AccommodationResponse(
        Long id,
        Long tripId,
        String name,
        LocalDate checkIn,
        LocalDate checkOut,
        BigDecimal cost,
        PlaceResponse place
) {
    public static AccommodationResponse from(Accommodation a) {
        return new AccommodationResponse(
                a.getId(), a.getTripId(), a.getName(),
                a.getCheckIn(), a.getCheckOut(), a.getCost(),
                PlaceResponse.from(a.getPlace()));
    }
}
