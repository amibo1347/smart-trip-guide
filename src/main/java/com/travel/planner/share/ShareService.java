package com.travel.planner.share;

import com.travel.planner.booking.dto.BookingResponse;
import com.travel.planner.booking.repository.TripBookingRepository;
import com.travel.planner.checklist.service.ChecklistService;
import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.currency.CurrencyService;
import com.travel.planner.planning.dto.PlanResponse;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.repository.TripRepository;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 일정 '읽기 전용 공유'. 소유자는 공유를 켜서 토큰을 발급/폐기하고,
 * 토큰을 가진 누구나(비로그인 포함) 일정을 열람할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareService {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final PlanRepository planRepository;
    private final TripBookingRepository bookingRepository;
    private final CurrencyService currencyService;
    private final ChecklistService checklistService;

    /** 공유 활성화(이미 켜져 있으면 기존 토큰 반환). */
    @Transactional
    public String enable(Long tripId, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        if (trip.getShareToken() == null || trip.getShareToken().isBlank()) {
            trip.enableShare(UUID.randomUUID().toString().replace("-", ""));
        }
        return trip.getShareToken();
    }

    /** 공유 중단(토큰 폐기 → 기존 링크 즉시 무효). */
    @Transactional
    public void disable(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId).disableShare();
    }

    /** 토큰으로 읽기 전용 일정 조회(비로그인 허용). */
    public SharedTripResponse getByToken(String token) {
        Trip trip = tripRepository.findByShareToken(token)
                .orElseThrow(() -> new NotFoundException("공유된 일정을 찾을 수 없습니다(만료되었거나 비공개일 수 있어요)."));

        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(trip.getId()).orElse(null);
        PlanResponse planRes = plan == null ? null : PlanResponse.from(plan);

        List<BookingResponse> bookings = bookingRepository.findByTripIdOrderByTypeAscIdAsc(trip.getId())
                .stream().map(BookingResponse::from).toList();

        String destination = (plan != null && plan.getDestinationCity() != null && !plan.getDestinationCity().isBlank())
                ? plan.getDestinationCity() : trip.getTitle();

        // 1인당 분담액: 활동비(예상) + 확정 예약을 인원수로 나눈 값. 공유받은 사람이 바로 확인할 수 있게 함께 내린다.
        BigDecimal plannedTotal = activityTotal(plan).add(
                bookings.stream().map(BookingResponse::price).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal perPerson = trip.getHeadcount() <= 0 ? null
                : plannedTotal.divide(BigDecimal.valueOf(trip.getHeadcount()), 0, RoundingMode.HALF_UP);

        return new SharedTripResponse(
                trip.getTitle(), trip.getStartDate(), trip.getEndDate(), trip.getHeadcount(), trip.getConcept(),
                planRes, bookings, currencyService.forDestination(destination),
                checklistService.listByTrip(trip.getId()), plannedTotal, perPerson);
    }

    /** 최신 계획의 활동비(estCost) 합계. */
    private static BigDecimal activityTotal(Plan plan) {
        if (plan == null) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (PlanDay day : plan.getDays()) {
            for (PlanItem item : day.getItems()) {
                if (item.getEstCost() != null) sum = sum.add(item.getEstCost());
            }
        }
        return sum;
    }
}
