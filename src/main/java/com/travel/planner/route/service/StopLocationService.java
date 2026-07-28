package com.travel.planner.route.service;

import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.PlaceProvider;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.repository.PlaceRepository;
import com.travel.planner.planning.repository.PlanItemRepository;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선도 정거장의 위치를 <b>수동으로</b> 지정/수정하거나 자동 탐색으로 되돌린다.
 *
 * <p>기본은 자동 지오코딩({@link RouteService})이지만, 못 찾았거나 엉뚱한 곳에 찍힌 장소를
 * 사용자가 지도에서 직접 바로잡을 수 있어야 해서 둔다. 수동 지정한 좌표는
 * geocodeAttempted=true 로 표시돼 이후 자동 지오코딩이 덮어쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StopLocationService {

    private final PlanItemRepository planItemRepository;
    private final PlaceRepository placeRepository;
    private final TripService tripService;

    /**
     * 일정 항목의 위치를 직접 지정한다. 장소가 아직 없던 항목이면 새로 만들어 붙인다.
     *
     * @param name    장소명(비우면 기존 값 또는 항목 제목)
     * @param address 주소(선택)
     */
    @Transactional
    public void setLocation(Long itemId, String name, String address,
                            BigDecimal latitude, BigDecimal longitude, Long userId) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("위도·경도가 필요합니다.");
        }
        if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("좌표 범위가 올바르지 않습니다.");
        }
        PlanItem item = requireOwnedItem(itemId, userId);

        Place place = item.getPlace();
        if (place == null) {
            place = placeRepository.save(Place.builder()
                    .provider(PlaceProvider.MANUAL)
                    .name(name != null && !name.isBlank() ? name.trim() : item.getTitle())
                    .address(address)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build());
            item.changePlace(place);
        } else {
            place.updateInfo(name, address);
            place.applyManualCoords(latitude, longitude);
        }
    }

    /** 수동 지정을 풀고 다음 노선도 조회 때 자동으로 다시 찾게 한다. */
    @Transactional
    public void resetToAuto(Long itemId, Long userId) {
        PlanItem item = requireOwnedItem(itemId, userId);
        Place place = item.getPlace();
        if (place != null) {
            place.resetToAuto();
        }
    }

    private PlanItem requireOwnedItem(Long itemId, Long userId) {
        PlanItem item = planItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("일정 항목을 찾을 수 없습니다: " + itemId));
        tripService.getOwnedTrip(item.getPlanDay().getPlan().getTripId(), userId); // 소유권 검증
        return item;
    }
}
