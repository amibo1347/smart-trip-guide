package com.travel.planner.trip.repository;

import com.travel.planner.trip.entity.Trip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByUserIdOrderByStartDateDesc(Long userId);
}
