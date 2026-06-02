package com.travel.planner.feedback.repository;

import com.travel.planner.feedback.entity.PlanItemActual;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanItemActualRepository extends JpaRepository<PlanItemActual, Long> {

    Optional<PlanItemActual> findByPlanItemId(Long planItemId);

    List<PlanItemActual> findByPlanItemIdIn(List<Long> planItemIds);
}
