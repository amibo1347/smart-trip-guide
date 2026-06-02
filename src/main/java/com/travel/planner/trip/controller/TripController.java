package com.travel.planner.trip.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.trip.dto.TripCreateRequest;
import com.travel.planner.trip.dto.TripResponse;
import com.travel.planner.trip.service.TripService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody TripCreateRequest request,
                                               Authentication auth,
                                               UriComponentsBuilder uriBuilder) {
        TripResponse created = tripService.create(currentUser.requireId(auth), request);
        var location = uriBuilder.path("/api/trips/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable Long id, Authentication auth) {
        return tripService.get(id, currentUser.requireId(auth));
    }

    /** 현재 로그인 사용자의 여행 목록. */
    @GetMapping
    public List<TripResponse> list(Authentication auth) {
        return tripService.listByUser(currentUser.requireId(auth));
    }
}
