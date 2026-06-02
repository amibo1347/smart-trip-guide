package com.travel.planner.tracking.repository;

import com.travel.planner.tracking.entity.Expense;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByTripIdAndClientUuid(Long tripId, String clientUuid);

    List<Expense> findByTripIdOrderBySpentAtDesc(Long tripId);
}
