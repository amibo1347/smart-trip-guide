package com.travel.planner.planning.repository;

import com.travel.planner.planning.entity.Accommodation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

    List<Accommodation> findByTripIdOrderByCheckInAsc(Long tripId);
}
