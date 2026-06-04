package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.entity.PlanItemType;
import java.math.BigDecimal;
import java.time.LocalTime;

public record PlanItemResponse(
        Long id,
        PlanItemType type,
        String title,
        LocalTime plannedStart,
        LocalTime plannedEnd,
        BigDecimal estCost,
        int sortOrder,
        Long bookingId,
        PlaceResponse place
) {
    public static PlanItemResponse from(PlanItem i) {
        return new PlanItemResponse(
                i.getId(), i.getType(), i.getTitle(),
                i.getPlannedStart(), i.getPlannedEnd(), i.getEstCost(), i.getSortOrder(),
                i.getBookingId(),
                PlaceResponse.from(i.getPlace()));
    }
}
