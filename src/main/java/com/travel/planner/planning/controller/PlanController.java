package com.travel.planner.planning.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.planning.dto.AccommodationRequest;
import com.travel.planner.planning.dto.AccommodationResponse;
import com.travel.planner.planning.dto.AddPlanItemRequest;
import com.travel.planner.planning.dto.GeneratePlanRequest;
import com.travel.planner.planning.dto.PlanResponse;
import com.travel.planner.planning.service.AccommodationService;
import com.travel.planner.planning.service.AiPlanService;
import com.travel.planner.planning.service.PlanService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    private final AccommodationService accommodationService;
    private final AiPlanService aiPlanService;
    private final CurrentUser currentUser;

    /** 여행의 현재 일정 (없으면 자동 생성). */
    @GetMapping("/api/trips/{tripId}/plan")
    public PlanResponse getPlan(@PathVariable Long tripId, Authentication auth) {
        return planService.getOrCreateCurrentPlan(tripId, currentUser.requireId(auth));
    }

    /** AI(Gemini)로 새 일정 버전 생성. */
    @PostMapping("/api/trips/{tripId}/plan/generate")
    public PlanResponse generatePlan(@PathVariable Long tripId,
                                     @Valid @RequestBody(required = false) GeneratePlanRequest request,
                                     Authentication auth) {
        String note = request == null ? null : request.note();
        return aiPlanService.generate(tripId, currentUser.requireId(auth), note);
    }

    /** 특정 일자에 일정 항목 추가. */
    @PostMapping("/api/plan-days/{dayId}/items")
    public PlanResponse addItem(@PathVariable Long dayId,
                                @Valid @RequestBody AddPlanItemRequest request,
                                Authentication auth) {
        return planService.addItem(dayId, request, currentUser.requireId(auth));
    }

    /** 일정 항목 삭제. */
    @DeleteMapping("/api/plan-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long itemId, Authentication auth) {
        planService.deleteItem(itemId, currentUser.requireId(auth));
    }

    /** 일정 항목 순서 이동. direction=UP|DOWN */
    @PatchMapping("/api/plan-items/{itemId}/move")
    public PlanResponse moveItem(@PathVariable Long itemId,
                                 @RequestParam String direction,
                                 Authentication auth) {
        return planService.moveItem(itemId, "UP".equalsIgnoreCase(direction), currentUser.requireId(auth));
    }

    // ── 숙박 ──
    @GetMapping("/api/trips/{tripId}/accommodations")
    public List<AccommodationResponse> listAccommodations(@PathVariable Long tripId, Authentication auth) {
        return accommodationService.list(tripId, currentUser.requireId(auth));
    }

    @PostMapping("/api/trips/{tripId}/accommodations")
    public AccommodationResponse addAccommodation(@PathVariable Long tripId,
                                                  @Valid @RequestBody AccommodationRequest request,
                                                  Authentication auth) {
        return accommodationService.add(tripId, request, currentUser.requireId(auth));
    }

    @DeleteMapping("/api/accommodations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccommodation(@PathVariable Long id, Authentication auth) {
        accommodationService.delete(id, currentUser.requireId(auth));
    }
}
