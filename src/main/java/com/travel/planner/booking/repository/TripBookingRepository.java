package com.travel.planner.booking.repository;

import com.travel.planner.booking.entity.TripBooking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripBookingRepository extends JpaRepository<TripBooking, Long> {

    List<TripBooking> findByTripIdOrderByTypeAscIdAsc(Long tripId);
}
