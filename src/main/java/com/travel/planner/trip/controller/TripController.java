package com.travel.planner.trip.controller;

import com.travel.planner.trip.dto.TripCreateRequest;
import com.travel.planner.trip.dto.TripResponse;
import com.travel.planner.trip.service.TripService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody TripCreateRequest request,
                                               UriComponentsBuilder uriBuilder) {
        TripResponse created = tripService.create(request);
        var location = uriBuilder.path("/api/trips/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable Long id) {
        return tripService.get(id);
    }

    /** 특정 사용자의 여행 목록. 예: GET /api/trips?userId=1 */
    @GetMapping
    public List<TripResponse> listByUser(@RequestParam Long userId) {
        return tripService.listByUser(userId);
    }
}
