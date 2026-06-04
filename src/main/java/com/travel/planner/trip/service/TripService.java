package com.travel.planner.trip.service;

import com.travel.planner.common.exception.ForbiddenException;
import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.trip.dto.TripCreateRequest;
import com.travel.planner.trip.dto.TripResponse;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.repository.TripRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;

    @Transactional
    public TripResponse create(Long userId, TripCreateRequest request) {
        Trip trip = Trip.builder()
                .userId(userId)
                .title(request.title())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .headcount(request.headcount())
                .budgetLimit(request.budgetLimit())
                .concept(request.concept())
                .build();
        return TripResponse.from(tripRepository.save(trip));
    }

    public TripResponse get(Long id, Long userId) {
        return TripResponse.from(getOwnedTrip(id, userId));
    }

    public List<TripResponse> listByUser(Long userId) {
        return tripRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .map(TripResponse::from)
                .toList();
    }

    public Trip getEntity(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다: " + id));
    }

    /** 여행 물리 삭제. FK ON DELETE CASCADE(V12)로 일정·예약·기록·사진참조까지 함께 제거된다. */
    @Transactional
    public void delete(Long tripId, Long userId) {
        Trip trip = getOwnedTrip(tripId, userId);
        tripRepository.delete(trip);
    }

    /** 소유권 검증: 본인 여행이 아니면 403. 하위 리소스(일정/기록/숙박)의 접근 게이트로 공용 사용. */
    public Trip getOwnedTrip(Long tripId, Long userId) {
        Trip trip = getEntity(tripId);
        if (!trip.getUserId().equals(userId)) {
            throw new ForbiddenException("해당 여행에 접근 권한이 없습니다.");
        }
        return trip;
    }
}
