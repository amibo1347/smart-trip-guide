package com.travel.planner.expense.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.expense.dto.ExpenseRequest;
import com.travel.planner.expense.dto.ExpenseResponse;
import com.travel.planner.expense.dto.ExpenseSummaryResponse;
import com.travel.planner.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가계부 API — 지출 내역 CRUD + N빵 정산 요약.
 */
@RestController
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CurrentUser currentUser;

    /** 지출 목록 + 정산 요약. */
    @GetMapping("/api/trips/{tripId}/expenses")
    public ExpenseSummaryResponse list(@PathVariable Long tripId, Authentication auth) {
        return expenseService.summary(tripId, currentUser.requireId(auth));
    }

    @PostMapping("/api/trips/{tripId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@PathVariable Long tripId,
                                  @Valid @RequestBody ExpenseRequest req, Authentication auth) {
        return expenseService.create(tripId, req, currentUser.requireId(auth));
    }

    @PutMapping("/api/expenses/{expenseId}")
    public ExpenseResponse update(@PathVariable Long expenseId,
                                  @Valid @RequestBody ExpenseRequest req, Authentication auth) {
        return expenseService.update(expenseId, req, currentUser.requireId(auth));
    }

    @DeleteMapping("/api/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long expenseId, Authentication auth) {
        expenseService.delete(expenseId, currentUser.requireId(auth));
    }
}
