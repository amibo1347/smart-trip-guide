package com.travel.planner.tracking.controller;

import com.travel.planner.tracking.dto.ExpenseRequest;
import com.travel.planner.tracking.dto.LocationRequest;
import com.travel.planner.tracking.dto.MoodRequest;
import com.travel.planner.tracking.dto.TrackingResponses.ExpenseResponse;
import com.travel.planner.tracking.dto.TrackingResponses.ExpenseSummary;
import com.travel.planner.tracking.dto.TrackingResponses.LocationResponse;
import com.travel.planner.tracking.dto.TrackingResponses.MoodResponse;
import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.tracking.service.TrackingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final CurrentUser currentUser;

    // ── 위치 ──
    @PostMapping("/locations")
    public LocationResponse recordLocation(@PathVariable Long tripId,
                                           @Valid @RequestBody LocationRequest req, Authentication auth) {
        return trackingService.recordLocation(tripId, req, currentUser.requireId(auth));
    }

    @GetMapping("/locations")
    public List<LocationResponse> listLocations(@PathVariable Long tripId, Authentication auth) {
        return trackingService.listLocations(tripId, currentUser.requireId(auth));
    }

    // ── 기분 ──
    @PostMapping("/moods")
    public MoodResponse recordMood(@PathVariable Long tripId,
                                   @Valid @RequestBody MoodRequest req, Authentication auth) {
        return trackingService.recordMood(tripId, req, currentUser.requireId(auth));
    }

    @GetMapping("/moods")
    public List<MoodResponse> listMoods(@PathVariable Long tripId, Authentication auth) {
        return trackingService.listMoods(tripId, currentUser.requireId(auth));
    }

    // ── 지출 ──
    @PostMapping("/expenses")
    public ExpenseResponse recordExpense(@PathVariable Long tripId,
                                         @Valid @RequestBody ExpenseRequest req, Authentication auth) {
        return trackingService.recordExpense(tripId, req, currentUser.requireId(auth));
    }

    @GetMapping("/expenses")
    public List<ExpenseResponse> listExpenses(@PathVariable Long tripId, Authentication auth) {
        return trackingService.listExpenses(tripId, currentUser.requireId(auth));
    }

    @GetMapping("/expenses/summary")
    public ExpenseSummary expenseSummary(@PathVariable Long tripId, Authentication auth) {
        return trackingService.expenseSummary(tripId, currentUser.requireId(auth));
    }
}
