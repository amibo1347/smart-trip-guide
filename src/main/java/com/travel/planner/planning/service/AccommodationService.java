package com.travel.planner.planning.service;

import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.planning.dto.AccommodationRequest;
import com.travel.planner.planning.dto.AccommodationResponse;
import com.travel.planner.planning.entity.Accommodation;
import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.PlaceProvider;
import com.travel.planner.planning.repository.AccommodationRepository;
import com.travel.planner.planning.repository.PlaceRepository;
import com.travel.planner.trip.service.TripService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final PlaceRepository placeRepository;
    private final TripService tripService;

    @Transactional
    public AccommodationResponse add(Long tripId, AccommodationRequest request, Long userId) {
        tripService.getOwnedTrip(tripId, userId); // 소유권 검증

        Place place = null;
        if (request.address() != null && !request.address().isBlank()) {
            place = placeRepository.save(Place.builder()
                    .provider(PlaceProvider.MANUAL)
                    .name(request.name())
                    .address(request.address())
                    .build());
        }

        Accommodation acc = Accommodation.builder()
                .tripId(tripId)
                .place(place)
                .name(request.name())
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .cost(request.cost())
                .build();
        return AccommodationResponse.from(accommodationRepository.save(acc));
    }

    public List<AccommodationResponse> list(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        return accommodationRepository.findByTripIdOrderByCheckInAsc(tripId).stream()
                .map(AccommodationResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long accommodationId, Long userId) {
        Accommodation acc = accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new NotFoundException("숙박을 찾을 수 없습니다: " + accommodationId));
        tripService.getOwnedTrip(acc.getTripId(), userId);
        accommodationRepository.delete(acc);
    }
}
