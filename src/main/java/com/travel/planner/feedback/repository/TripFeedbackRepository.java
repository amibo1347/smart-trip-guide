package com.travel.planner.feedback.repository;

import com.travel.planner.feedback.entity.TripFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripFeedbackRepository extends JpaRepository<TripFeedback, Long> {

    Optional<TripFeedback> findByTripId(Long tripId);
}
