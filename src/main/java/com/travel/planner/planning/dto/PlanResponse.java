package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.GeneratedBy;
import com.travel.planner.planning.entity.Plan;
import java.math.BigDecimal;
import java.util.List;

public record PlanResponse(
        Long id,
        Long tripId,
        int version,
        GeneratedBy generatedBy,
        String destinationCity,
        BigDecimal destinationLat,
        BigDecimal destinationLng,
        List<PlanDayResponse> days
) {
    public static PlanResponse from(Plan p) {
        return new PlanResponse(
                p.getId(), p.getTripId(), p.getVersion(), p.getGeneratedBy(), p.getDestinationCity(),
                p.getDestinationLat(), p.getDestinationLng(),
                p.getDays().stream().map(PlanDayResponse::from).toList());
    }
}
