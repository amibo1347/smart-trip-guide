package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.GeneratedBy;
import com.travel.planner.planning.entity.Plan;
import java.util.List;

public record PlanResponse(
        Long id,
        Long tripId,
        int version,
        GeneratedBy generatedBy,
        List<PlanDayResponse> days
) {
    public static PlanResponse from(Plan p) {
        return new PlanResponse(
                p.getId(), p.getTripId(), p.getVersion(), p.getGeneratedBy(),
                p.getDays().stream().map(PlanDayResponse::from).toList());
    }
}
