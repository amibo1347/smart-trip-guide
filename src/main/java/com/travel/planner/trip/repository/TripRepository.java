package com.travel.planner.trip.repository;

import com.travel.planner.trip.entity.Trip;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByUserIdOrderByStartDateDesc(Long userId);

    /** 공유 토큰으로 여행 조회(읽기 전용 공유 뷰용). */
    Optional<Trip> findByShareToken(String shareToken);
}
