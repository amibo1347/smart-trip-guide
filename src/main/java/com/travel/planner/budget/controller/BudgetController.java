package com.travel.planner.budget.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.budget.dto.BudgetSummaryResponse;
import com.travel.planner.budget.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final CurrentUser currentUser;

    /** 계획 단계 예산 점검: 활동비 + 확정 예약 합계 vs 예산. */
    @GetMapping("/api/trips/{tripId}/budget")
    public BudgetSummaryResponse get(@PathVariable Long tripId, Authentication auth) {
        return budgetService.getSummary(tripId, currentUser.requireId(auth));
    }
}
