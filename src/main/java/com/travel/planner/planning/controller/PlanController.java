package com.travel.planner.planning.controller;

import com.travel.planner.planning.dto.AddPlanItemRequest;
import com.travel.planner.planning.dto.PlanResponse;
import com.travel.planner.planning.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /** 여행의 현재 일정 (없으면 자동 생성). */
    @GetMapping("/api/trips/{tripId}/plan")
    public PlanResponse getPlan(@PathVariable Long tripId) {
        return planService.getOrCreateCurrentPlan(tripId);
    }

    /** 특정 일자에 일정 항목 추가. */
    @PostMapping("/api/plan-days/{dayId}/items")
    public PlanResponse addItem(@PathVariable Long dayId,
                                @Valid @RequestBody AddPlanItemRequest request) {
        return planService.addItem(dayId, request);
    }

    /** 일정 항목 삭제. */
    @DeleteMapping("/api/plan-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long itemId) {
        planService.deleteItem(itemId);
    }
}
