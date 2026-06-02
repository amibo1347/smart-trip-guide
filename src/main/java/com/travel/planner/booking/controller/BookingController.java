package com.travel.planner.booking.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.booking.dto.BookingRequest;
import com.travel.planner.booking.dto.BookingResponse;
import com.travel.planner.booking.dto.LinkPreviewRequest;
import com.travel.planner.booking.dto.LinkPreviewResponse;
import com.travel.planner.booking.service.BookingService;
import com.travel.planner.booking.service.LinkPreviewService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final LinkPreviewService linkPreviewService;
    private final CurrentUser currentUser;

    /** 붙여넣은 예약 페이지 링크의 미리보기(제목/이미지/설명) 가져오기. */
    @PostMapping("/api/link-preview")
    public LinkPreviewResponse preview(@Valid @RequestBody LinkPreviewRequest req, Authentication auth) {
        currentUser.requireId(auth); // 인증 필요
        return linkPreviewService.fetch(req.url());
    }

    @GetMapping("/api/trips/{tripId}/bookings")
    public List<BookingResponse> list(@PathVariable Long tripId, Authentication auth) {
        return bookingService.list(tripId, currentUser.requireId(auth));
    }

    @PostMapping("/api/trips/{tripId}/bookings")
    public BookingResponse add(@PathVariable Long tripId,
                               @Valid @RequestBody BookingRequest req, Authentication auth) {
        return bookingService.add(tripId, req, currentUser.requireId(auth));
    }

    @DeleteMapping("/api/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        bookingService.delete(id, currentUser.requireId(auth));
    }
}
