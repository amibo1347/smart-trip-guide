package com.travel.planner.tracking.repository;

import com.travel.planner.tracking.entity.LocationLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationLogRepository extends JpaRepository<LocationLog, Long> {

    Optional<LocationLog> findByTripIdAndClientUuid(Long tripId, String clientUuid);

    List<LocationLog> findByTripIdOrderByRecordedAtDesc(Long tripId);
}
