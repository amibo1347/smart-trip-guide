package com.travel.planner.booking.service;

import com.travel.planner.booking.dto.BookingRequest;
import com.travel.planner.booking.dto.BookingResponse;
import com.travel.planner.booking.entity.TripBooking;
import com.travel.planner.booking.repository.TripBookingRepository;
import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.trip.service.TripService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private final TripBookingRepository bookingRepository;
    private final TripService tripService;

    public List<BookingResponse> list(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        return bookingRepository.findByTripIdOrderByTypeAscIdAsc(tripId).stream()
                .map(BookingResponse::from).toList();
    }

    @Transactional
    public BookingResponse add(Long tripId, BookingRequest req, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        TripBooking booking = TripBooking.builder()
                .tripId(tripId)
                .type(req.type())
                .title(req.title())
                .price(req.price())
                .bookingUrl(req.bookingUrl())
                .imageUrl(req.imageUrl())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .memo(req.memo())
                .build();
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public void delete(Long bookingId, Long userId) {
        TripBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("예약을 찾을 수 없습니다: " + bookingId));
        tripService.getOwnedTrip(booking.getTripId(), userId);
        bookingRepository.delete(booking);
    }
}
