package com.travel.planner.planning.repository;

import com.travel.planner.planning.entity.PlanDay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanDayRepository extends JpaRepository<PlanDay, Long> {
}
