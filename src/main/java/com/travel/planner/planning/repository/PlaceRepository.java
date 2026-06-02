package com.travel.planner.planning.repository;

import com.travel.planner.planning.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
