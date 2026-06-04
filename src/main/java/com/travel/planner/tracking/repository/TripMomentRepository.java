package com.travel.planner.tracking.repository;

import com.travel.planner.tracking.entity.TripMoment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripMomentRepository extends JpaRepository<TripMoment, Long> {

    Optional<TripMoment> findByTripIdAndClientUuid(Long tripId, String clientUuid);

    List<TripMoment> findByTripIdOrderByRecordedAtDesc(Long tripId);

    /** 여행 중 실제 지출 합계(amount 입력된 기록만). 기록이 없으면 0. */
    @Query("select coalesce(sum(m.amount), 0) from TripMoment m where m.tripId = :tripId")
    BigDecimal sumAmountByTripId(@Param("tripId") Long tripId);
}
