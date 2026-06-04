package com.travel.planner.currency;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.currency.CurrencyService.CurrencyInfo;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 목적지에 맞는 통화·환율 제공(원화 + 외화 병기용).
 */
@RestController
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;
    private final TripService tripService;
    private final PlanRepository planRepository;
    private final CurrentUser currentUser;

    @GetMapping("/api/trips/{tripId}/currency")
    public CurrencyInfo forTrip(@PathVariable Long tripId, Authentication auth) {
        Trip trip = tripService.getOwnedTrip(tripId, currentUser.requireId(auth));
        // 목적지: 최신 AI 플랜이 파악한 도시 → 없으면 여행 제목
        String destination = planRepository.findTopByTripIdOrderByVersionDesc(tripId)
                .map(Plan::getDestinationCity)
                .filter(s -> s != null && !s.isBlank())
                .orElse(trip.getTitle());
        return currencyService.forDestination(destination);
    }
}
