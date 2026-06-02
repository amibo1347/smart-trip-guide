package com.travel.planner.tracking.repository;

import com.travel.planner.tracking.entity.MoodLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodLogRepository extends JpaRepository<MoodLog, Long> {

    Optional<MoodLog> findByTripIdAndClientUuid(Long tripId, String clientUuid);

    List<MoodLog> findByTripIdOrderByRecordedAtDesc(Long tripId);
}
