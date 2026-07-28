package com.travel.planner.expense.repository;

import com.travel.planner.expense.entity.Expense;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByTripIdOrderBySpentOnDescIdDesc(Long tripId);

    /** 여행 가계부 지출 합계. 내역이 없으면 0. */
    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.tripId = :tripId")
    BigDecimal sumAmountByTripId(@Param("tripId") Long tripId);
}
