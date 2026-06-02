package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.PlanDay;
import java.time.LocalDate;
import java.util.List;

public record PlanDayResponse(
        Long id,
        int dayNo,
        LocalDate date,
        List<PlanItemResponse> items
) {
    public static PlanDayResponse from(PlanDay d) {
        return new PlanDayResponse(
                d.getId(), d.getDayNo(), d.getDate(),
                d.getItems().stream().map(PlanItemResponse::from).toList());
    }
}
