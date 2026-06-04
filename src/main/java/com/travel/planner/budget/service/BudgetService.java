package com.travel.planner.budget.service;

import com.travel.planner.booking.entity.TripBooking;
import com.travel.planner.booking.repository.TripBookingRepository;
import com.travel.planner.budget.dto.BudgetSummaryResponse;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.tracking.repository.TripMomentRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계획 단계 예산 점검: 최신 Plan의 활동비(estCost) 합계 + 확정 예약 가격 합계를
 * 여행 예산 한도와 비교한다. DB 값만 합산하므로 외부 키/스크래핑이 필요 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {

    private final TripService tripService;
    private final PlanRepository planRepository;
    private final TripBookingRepository bookingRepository;
    private final TripMomentRepository momentRepository;

    public BudgetSummaryResponse getSummary(Long tripId, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);

        BigDecimal activityTotal = BigDecimal.ZERO;
        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(tripId).orElse(null);
        if (plan != null) {
            for (PlanDay day : plan.getDays()) {
                for (PlanItem it : day.getItems()) {
                    if (it.getEstCost() != null) {
                        activityTotal = activityTotal.add(it.getEstCost());
                    }
                }
            }
        }

        BigDecimal bookingTotal = bookingRepository.findByTripIdOrderByTypeAscIdAsc(tripId).stream()
                .map(TripBooking::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal plannedTotal = activityTotal.add(bookingTotal);
        BigDecimal limit = trip.getBudgetLimit();
        BigDecimal remaining = limit == null ? null : limit.subtract(plannedTotal);
        boolean overBudget = remaining != null && remaining.signum() < 0;
        BigDecimal overAmount = overBudget ? remaining.negate() : BigDecimal.ZERO;

        // 여행 중 실시간 집행: 확정 예약(이미 결제) + 실제 지출(moments) vs 한도
        BigDecimal actualSpent = momentRepository.sumAmountByTripId(tripId);
        if (actualSpent == null) actualSpent = BigDecimal.ZERO;
        BigDecimal liveTotal = bookingTotal.add(actualSpent);
        BigDecimal liveRemaining = limit == null ? null : limit.subtract(liveTotal);
        boolean liveOverBudget = liveRemaining != null && liveRemaining.signum() < 0;
        BigDecimal liveOverAmount = liveOverBudget ? liveRemaining.negate() : BigDecimal.ZERO;
        Double usedRatio = (limit == null || limit.signum() <= 0) ? null
                : liveTotal.multiply(BigDecimal.valueOf(100))
                        .divide(limit, 1, RoundingMode.HALF_UP).doubleValue();

        return new BudgetSummaryResponse(
                limit, activityTotal, bookingTotal, plannedTotal, remaining, overBudget, overAmount,
                actualSpent, liveTotal, liveRemaining, liveOverBudget, liveOverAmount, usedRatio);
    }
}
