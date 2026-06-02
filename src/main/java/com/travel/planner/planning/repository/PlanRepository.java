package com.travel.planner.planning.repository;

import com.travel.planner.planning.entity.Plan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    /** 해당 여행의 최신(최고 버전) 일정. */
    Optional<Plan> findTopByTripIdOrderByVersionDesc(Long tripId);
}
