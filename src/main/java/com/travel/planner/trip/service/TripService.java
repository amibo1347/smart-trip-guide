package com.travel.planner.trip.service;

import com.travel.planner.account.service.UserService;
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
    private final UserService userService;

    @Transactional
    public TripResponse create(TripCreateRequest request) {
        // 사용자 존재 검증 (없으면 404)
        userService.getEntity(request.userId());

        Trip trip = Trip.builder()
                .userId(request.userId())
                .title(request.title())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .headcount(request.headcount())
                .budgetLimit(request.budgetLimit())
                .concept(request.concept())
                .build();
        return TripResponse.from(tripRepository.save(trip));
    }

    public TripResponse get(Long id) {
        return TripResponse.from(getEntity(id));
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
}
