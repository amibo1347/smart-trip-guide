package com.travel.planner.route.service;

import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.route.dto.RouteDtos.RecordResponse;
import com.travel.planner.route.dto.RouteDtos.RouteResponse;
import com.travel.planner.route.dto.RouteDtos.Stop;
import com.travel.planner.route.dto.RouteDtos.UnlocatedStop;
import com.travel.planner.tracking.dto.MomentResponses.MomentResponse;
import com.travel.planner.tracking.entity.TripMoment;
import com.travel.planner.tracking.repository.TripMomentRepository;
import com.travel.planner.tracking.service.GeocodingService;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 기반 노선도. 일정 항목의 장소를 좌표로 변환(지오코딩)해 방문 순서대로 잇고,
 * 각 장소에 남긴 기록(사진·지출·메모)을 함께 내려준다. 일정이 바뀌면(항목 추가/삭제/재생성) 이 응답도 자동으로 달라진다.
 *
 * <p>기록은 별도 테이블이 아니라 통합 기록(trip_moments)에 plan_item_id 로 연결된다 —
 * 한 장소에 사진+지출+메모를 하나로 남기고, 그 지출은 예산 집계에도 그대로 반영된다.
 *
 * <p>지오코딩은 외부(OSM Nominatim, 약 1req/s 권장)를 쓰므로 한 번 호출에 소수(BATCH)만 변환하고
 * 결과를 DB(Place)에 저장한다. 남은 개수는 pending 으로 알려 프런트가 다시 요청하게 한다(진행률 표시).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteService {

    /** 한 번 요청에서 새로 지오코딩할 장소 수 상한(Nominatim 정책 배려 + 응답 지연 억제). */
    private static final int GEOCODE_BATCH = 5;

    private final TripService tripService;
    private final PlanRepository planRepository;
    private final TripMomentRepository momentRepository;
    private final GeocodingService geocodingService;

    @Transactional
    public RouteResponse getRoute(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(tripId).orElse(null);
        if (plan == null) {
            return new RouteResponse(false, null, null, List.of(), List.of(), 0);
        }

        // 방문 순서: 일자(dayNo) → 항목 정렬순(sortOrder) → id
        List<DayItem> ordered = new ArrayList<>();
        plan.getDays().stream()
                .sorted(Comparator.comparing(PlanDay::getDayNo))
                .forEach(day -> day.getItems().stream()
                        .sorted(Comparator.comparingInt(PlanItem::getSortOrder).thenComparing(PlanItem::getId))
                        .forEach(item -> ordered.add(new DayItem(day, item))));

        // 좌표가 없고 아직 시도하지 않은 장소를 이번 배치만큼 지오코딩(장소명 + 목적지 도시로 정확도 보강).
        int pending = geocodeMissing(ordered, plan.getDestinationCity());

        // 항목별 기록(moment) 묶기 — 한 번 조회 후 plan_item_id 로 그룹.
        Map<Long, List<RecordResponse>> recordsByItem = momentRepository.findByTripIdOrderByRecordedAtDesc(tripId).stream()
                .filter(m -> m.getPlanItemId() != null)
                .collect(Collectors.groupingBy(TripMoment::getPlanItemId,
                        Collectors.mapping(m -> RecordResponse.from(MomentResponse.from(m)), Collectors.toList())));

        List<Stop> stops = new ArrayList<>();
        List<UnlocatedStop> unlocated = new ArrayList<>();
        int order = 1;
        for (DayItem di : ordered) {
            PlanItem it = di.item();
            Place p = it.getPlace();
            List<RecordResponse> records = recordsByItem.getOrDefault(it.getId(), List.of());
            if (p != null && p.hasCoords()) {
                stops.add(new Stop(it.getId(), order++, di.day().getDayNo(), di.day().getDate(),
                        it.getTitle(), it.getType(), it.getPlannedStart(),
                        p.getLatitude(), p.getLongitude(), p.getAddress(), records));
            } else {
                unlocated.add(new UnlocatedStop(it.getId(), di.day().getDayNo(), di.day().getDate(),
                        it.getTitle(), it.getType(), records));
            }
        }

        return new RouteResponse(true, plan.getDestinationLat(), plan.getDestinationLng(),
                stops, unlocated, pending);
    }

    /**
     * 좌표가 없는 장소를 최대 BATCH 개 지오코딩해 저장한다.
     * @return 이번에 변환하지 못하고 남은(아직 시도 안 한) 장소 수 = 다음 호출에서 처리될 개수.
     */
    private int geocodeMissing(List<DayItem> ordered, String destinationCity) {
        List<Place> targets = ordered.stream()
                .map(di -> di.item().getPlace())
                .filter(p -> p != null && !p.hasCoords() && !p.isGeocodeAttempted() && notBlank(p.getName()))
                .toList();

        int done = 0;
        for (Place place : targets) {
            if (done >= GEOCODE_BATCH) {
                break; // 남은 것은 다음 요청에서
            }
            done++;
            BigDecimal[] coord = geocodingService.forward(geocodeQuery(place, destinationCity));
            if (coord != null) {
                place.applyGeocode(coord[0], coord[1]);
            } else {
                place.markGeocodeAttempted(); // 실패 — 다음부터 재시도하지 않음
            }
        }
        return Math.max(0, targets.size() - done);
    }

    /** 장소명 뒤에 목적지 도시를 붙여 동명 지명 오매칭을 줄인다(주소가 있으면 주소 우선). */
    private static String geocodeQuery(Place place, String destinationCity) {
        if (notBlank(place.getAddress())) {
            return place.getAddress();
        }
        String q = place.getName();
        if (notBlank(destinationCity) && !q.contains(destinationCity)) {
            q = q + " " + destinationCity;
        }
        return q;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 방문 순서 계산용 (일자 + 항목) 묶음. */
    private record DayItem(PlanDay day, PlanItem item) {
    }
}
