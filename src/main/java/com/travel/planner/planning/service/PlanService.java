package com.travel.planner.planning.service;

import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.planning.dto.AddPlanItemRequest;
import com.travel.planner.planning.dto.PlaceInput;
import com.travel.planner.planning.dto.PlanResponse;
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
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.time.LocalDate;
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
    public PlanResponse getOrCreateCurrentPlan(Long tripId) {
        Trip trip = tripService.getEntity(tripId);
        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(tripId)
                .orElseGet(() -> createInitialPlan(trip));
        return PlanResponse.from(plan);
    }

    private Plan createInitialPlan(Trip trip) {
        Plan plan = Plan.builder()
                .tripId(trip.getId())
                .version(1)
                .generatedBy(GeneratedBy.USER)
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
    public PlanResponse addItem(Long planDayId, AddPlanItemRequest request) {
        PlanDay day = planDayRepository.findById(planDayId)
                .orElseThrow(() -> new NotFoundException("일자를 찾을 수 없습니다: " + planDayId));

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

    @Transactional
    public void deleteItem(Long planItemId) {
        PlanItem item = planItemRepository.findById(planItemId)
                .orElseThrow(() -> new NotFoundException("일정 항목을 찾을 수 없습니다: " + planItemId));
        planItemRepository.delete(item);
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
