package com.travel.planner.planning.service;

import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.planning.dto.AddPlanItemRequest;
import com.travel.planner.planning.dto.PlaceInput;
import com.travel.planner.planning.dto.PlanResponse;
import com.travel.planner.planning.dto.UpdatePlanItemRequest;
import com.travel.planner.planning.entity.GeneratedBy;
import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.PlaceProvider;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.repository.PlanDayRepository;
import com.travel.planner.planning.repository.PlanItemRepository;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.planning.repository.PlaceRepository;
import com.travel.planner.planning.entity.PlanItemType;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanDayRepository planDayRepository;
    private final PlanItemRepository planItemRepository;
    private final PlaceRepository placeRepository;
    private final TripService tripService;

    /**
     * 여행의 현재 일정 조회. 없으면 v1(USER)을 만들고 여행 기간만큼 일자를 자동 생성한다.
     */
    @Transactional
    public PlanResponse getOrCreateCurrentPlan(Long tripId, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(tripId)
                .orElseGet(() -> createInitialPlan(trip));
        return PlanResponse.from(plan);
    }

    /**
     * 확정 예약(항공/숙소)을 현재 일정에 항목으로 반영.
     * 호텔=체크인·체크아웃 STAY, 항공=출발·귀국 MOVE. 비용은 0(가격은 예약 합계에 이미 포함 → 이중계산 방지).
     * 같은 예약 항목이 이미 그 플랜에 있으면 중복 생성하지 않는다.
     */
    @Transactional
    public void addBookingItems(Long tripId, Long userId, Long bookingId, boolean isHotel,
                                String title, LocalDate startDate, LocalDate endDate) {
        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(tripId)
                .orElseGet(() -> createInitialPlan(tripService.getOwnedTrip(tripId, userId)));
        addItemsForBooking(plan, bookingId, isHotel, title, startDate, endDate);
    }

    /** 확정 예약 삭제 시, 그 예약에서 생성된 일정 항목들을 모두 제거(모든 버전). */
    @Transactional
    public void removeBookingItems(Long bookingId) {
        planItemRepository.deleteAll(planItemRepository.findByBookingId(bookingId));
    }

    private void addItemsForBooking(Plan plan, Long bookingId, boolean isHotel, String title,
                                    LocalDate startDate, LocalDate endDate) {
        for (PlanDay d : plan.getDays()) {
            for (PlanItem it : d.getItems()) {
                if (bookingId.equals(it.getBookingId())) {
                    return; // 이 플랜에 이미 반영됨
                }
            }
        }
        Map<LocalDate, PlanDay> byDate = new HashMap<>();
        for (PlanDay d : plan.getDays()) {
            byDate.put(d.getDate(), d);
        }
        String name = (title == null || title.isBlank()) ? (isHotel ? "숙소" : "항공") : title;
        if (isHotel) {
            addBookingItem(byDate.get(startDate), bookingId, PlanItemType.STAY, "🏨 " + name + " 체크인");
            if (endDate != null && !endDate.equals(startDate)) {
                addBookingItem(byDate.get(endDate), bookingId, PlanItemType.STAY, "🏨 " + name + " 체크아웃");
            }
        } else {
            addBookingItem(byDate.get(startDate), bookingId, PlanItemType.MOVE, "✈️ " + name + " 출발");
            if (endDate != null && !endDate.equals(startDate)) {
                addBookingItem(byDate.get(endDate), bookingId, PlanItemType.MOVE, "✈️ " + name + " 귀국");
            }
        }
    }

    private void addBookingItem(PlanDay day, Long bookingId, PlanItemType type, String title) {
        if (day == null) {
            return; // 예약 날짜가 일정 범위 밖 → 건너뜀(예약은 '확정 예약'에 그대로 남음)
        }
        PlanItem item = PlanItem.builder()
                .bookingId(bookingId)
                .type(type)
                .title(title)
                .estCost(BigDecimal.ZERO) // 가격은 예약 합계로만 집계(이중계산 방지)
                .sortOrder(day.getItems().size())
                .build();
        day.addItem(item);
        planItemRepository.save(item);
    }

    private Plan createInitialPlan(Trip trip) {
        // 여행에 목적지가 있으면 그 좌표를 플랜 중심으로 심는다 → 노선도가 목적지 기준으로 뜬다.
        Plan plan = Plan.builder()
                .tripId(trip.getId())
                .version(1)
                .generatedBy(GeneratedBy.USER)
                .destinationCity(trip.getDestinationName())
                .destinationLat(trip.getDestinationLat())
                .destinationLng(trip.getDestinationLng())
                .build();

        int dayNo = 1;
        for (LocalDate d = trip.getStartDate(); !d.isAfter(trip.getEndDate()); d = d.plusDays(1)) {
            plan.addDay(PlanDay.builder().dayNo(dayNo++).date(d).build());
        }
        return planRepository.save(plan); // cascade 로 days 함께 저장
    }

    /**
     * 특정 일자에 일정 항목 추가. place 가 있으면 수동 Place 생성 후 연결.
     */
    @Transactional
    public PlanResponse addItem(Long planDayId, AddPlanItemRequest request, Long userId) {
        PlanDay day = planDayRepository.findById(planDayId)
                .orElseThrow(() -> new NotFoundException("일자를 찾을 수 없습니다: " + planDayId));
        tripService.getOwnedTrip(day.getPlan().getTripId(), userId); // 소유권 검증

        Place place = createPlaceIfPresent(request.place());

        PlanItem item = PlanItem.builder()
                .place(place)
                .type(request.type())
                .title(request.title())
                .plannedStart(request.plannedStart())
                .plannedEnd(request.plannedEnd())
                .estCost(request.estCost())
                .sortOrder(day.getItems().size())
                .build();
        day.addItem(item);
        planItemRepository.save(item);

        return PlanResponse.from(day.getPlan());
    }

    /** 일정 항목 편집(제목/시간/예상비용). 예산 점검에 즉시 반영된다. */
    @Transactional
    public PlanResponse updateItem(Long planItemId, UpdatePlanItemRequest request, Long userId) {
        PlanItem item = planItemRepository.findById(planItemId)
                .orElseThrow(() -> new NotFoundException("일정 항목을 찾을 수 없습니다: " + planItemId));
        PlanDay day = item.getPlanDay();
        tripService.getOwnedTrip(day.getPlan().getTripId(), userId); // 소유권 검증
        item.update(request.title(), request.plannedStart(), request.plannedEnd(), request.estCost());
        return PlanResponse.from(day.getPlan());
    }

    @Transactional
    public void deleteItem(Long planItemId, Long userId) {
        PlanItem item = planItemRepository.findById(planItemId)
                .orElseThrow(() -> new NotFoundException("일정 항목을 찾을 수 없습니다: " + planItemId));
        tripService.getOwnedTrip(item.getPlanDay().getPlan().getTripId(), userId);
        planItemRepository.delete(item);
    }

    /**
     * 같은 일자 안에서 항목 순서를 위/아래로 이동(인접 항목과 sort_order 교환).
     */
    @Transactional
    public PlanResponse moveItem(Long planItemId, boolean up, Long userId) {
        PlanItem item = planItemRepository.findById(planItemId)
                .orElseThrow(() -> new NotFoundException("일정 항목을 찾을 수 없습니다: " + planItemId));
        PlanDay day = item.getPlanDay();
        tripService.getOwnedTrip(day.getPlan().getTripId(), userId);

        List<PlanItem> ordered = day.getItems().stream()
                .sorted(Comparator.comparingInt(PlanItem::getSortOrder).thenComparing(PlanItem::getId))
                .toList();
        int idx = ordered.indexOf(item);
        int swapIdx = up ? idx - 1 : idx + 1;
        if (swapIdx >= 0 && swapIdx < ordered.size()) {
            PlanItem other = ordered.get(swapIdx);
            int tmp = item.getSortOrder();
            item.changeSortOrder(other.getSortOrder());
            other.changeSortOrder(tmp);
        }
        return PlanResponse.from(day.getPlan());
    }

    private Place createPlaceIfPresent(PlaceInput input) {
        if (input == null) {
            return null;
        }
        Place place = Place.builder()
                .provider(PlaceProvider.MANUAL)
                .name(input.name())
                .category(input.category())
                .address(input.address())
                .latitude(input.latitude())
                .longitude(input.longitude())
                .build();
        return placeRepository.save(place);
    }
}
