package com.travel.planner.checklist.repository;

import com.travel.planner.checklist.entity.ChecklistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findByTripIdOrderByCategoryAscSortOrderAscIdAsc(Long tripId);

    long countByTripId(Long tripId);
}
