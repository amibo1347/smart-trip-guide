package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import java.time.LocalDate;
import java.util.Comparator;
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
                d.getItems().stream()
                        .sorted(Comparator.comparingInt(PlanItem::getSortOrder).thenComparing(PlanItem::getId))
                        .map(PlanItemResponse::from)
                        .toList());
    }
}
