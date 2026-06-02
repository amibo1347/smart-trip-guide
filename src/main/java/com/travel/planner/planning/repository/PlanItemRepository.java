package com.travel.planner.planning.repository;

import com.travel.planner.planning.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {
}
