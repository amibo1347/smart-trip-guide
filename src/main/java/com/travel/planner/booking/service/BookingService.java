package com.travel.planner.booking.service;

import com.travel.planner.booking.dto.BookingRequest;
import com.travel.planner.booking.dto.BookingResponse;
import com.travel.planner.booking.entity.TripBooking;
import com.travel.planner.booking.entity.BookingType;
import com.travel.planner.booking.repository.TripBookingRepository;
import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.planning.service.PlanService;
import com.travel.planner.tracking.service.PhotoStorageService;
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
    private final PhotoStorageService photoStorage;
    private final PlanService planService;

    public List<BookingResponse> list(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        return bookingRepository.findByTripIdOrderByTypeAscIdAsc(tripId).stream()
                .map(BookingResponse::from).toList();
    }

    /** photoUrl 이 있으면(직접 올린 사진) 그것을 대표 이미지로, 없으면 req.imageUrl() 사용. */
    @Transactional
    public BookingResponse add(Long tripId, BookingRequest req, String photoUrl, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        TripBooking booking = TripBooking.builder()
                .tripId(tripId)
                .type(req.type())
                .title(req.title())
                .price(req.price())
                .bookingUrl(req.bookingUrl())
                .imageUrl(photoUrl != null ? photoUrl : req.imageUrl())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .memo(req.memo())
                .build();
        TripBooking saved = bookingRepository.save(booking);
        // 확정 예약을 일정에 자동 반영(호텔 체크인/항공 출발 등). 비용은 0으로 들어가 예산 이중계산 방지.
        planService.addBookingItems(tripId, userId, saved.getId(),
                saved.getType() == BookingType.HOTEL, saved.getTitle(),
                saved.getStartDate(), saved.getEndDate());
        return BookingResponse.from(saved);
    }

    /** 예약 확인증(이미지/PDF) 첨부·교체. 기존 확인증이 있으면 파일을 지우고 새로 저장. */
    @Transactional
    public BookingResponse attachTicket(Long bookingId, org.springframework.web.multipart.MultipartFile file, Long userId) {
        TripBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("예약을 찾을 수 없습니다: " + bookingId));
        tripService.getOwnedTrip(booking.getTripId(), userId);
        String url = photoStorage.storeDocument(file);
        if (url == null) {
            throw new IllegalArgumentException("확인증 파일이 없습니다.");
        }
        if (booking.getTicketUrl() != null) {
            photoStorage.delete(booking.getTicketUrl()); // 이전 확인증 파일 정리
        }
        booking.attachTicket(url);
        return BookingResponse.from(booking);
    }

    @Transactional
    public void removeTicket(Long bookingId, Long userId) {
        TripBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("예약을 찾을 수 없습니다: " + bookingId));
        tripService.getOwnedTrip(booking.getTripId(), userId);
        if (booking.getTicketUrl() != null) {
            photoStorage.delete(booking.getTicketUrl());
            booking.removeTicket();
        }
    }

    @Transactional
    public void delete(Long bookingId, Long userId) {
        TripBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("예약을 찾을 수 없습니다: " + bookingId));
        tripService.getOwnedTrip(booking.getTripId(), userId);
        planService.removeBookingItems(booking.getId()); // 이 예약에서 생성된 일정 항목도 정리
        photoStorage.delete(booking.getImageUrl()); // 직접 올린 사진(/uploads/..)이면 파일도 정리
        photoStorage.delete(booking.getTicketUrl()); // 확인증 파일도 정리
        bookingRepository.delete(booking);
    }
}
