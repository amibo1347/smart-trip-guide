package com.travel.planner.booking.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.booking.dto.BookingRequest;
import com.travel.planner.booking.dto.BookingResponse;
import com.travel.planner.booking.dto.LinkTitleRequest;
import com.travel.planner.booking.service.BookingService;
import com.travel.planner.booking.service.LinkTitleService;
import com.travel.planner.tracking.service.PhotoStorageService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PhotoStorageService photoStorage;
    private final LinkTitleService linkTitleService;
    private final CurrentUser currentUser;

    /** 붙여넣은 예약 페이지 링크에서 '이름(제목)'만 가져오기(사진/가격 미리보기 없음). */
    @PostMapping("/api/link-title")
    public Map<String, String> linkTitle(@Valid @RequestBody LinkTitleRequest req, Authentication auth) {
        currentUser.requireId(auth); // 인증 필요
        return Map.of("title", linkTitleService.fetchTitle(req.url()));
    }

    @GetMapping("/api/trips/{tripId}/bookings")
    public List<BookingResponse> list(@PathVariable Long tripId, Authentication auth) {
        return bookingService.list(tripId, currentUser.requireId(auth));
    }

    /** 사진 포함 저장 — data 파트=JSON 필드, photo 파트=직접 올린 이미지(선택). */
    @PostMapping(path = "/api/trips/{tripId}/bookings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookingResponse addWithPhoto(@PathVariable Long tripId,
                                        @RequestPart("data") @Valid BookingRequest req,
                                        @RequestPart(value = "photo", required = false) MultipartFile photo,
                                        Authentication auth) {
        String photoUrl = photoStorage.store(photo);
        return bookingService.add(tripId, req, photoUrl, currentUser.requireId(auth));
    }

    /** 사진 없는 저장(JSON). */
    @PostMapping(path = "/api/trips/{tripId}/bookings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BookingResponse add(@PathVariable Long tripId,
                               @Valid @RequestBody BookingRequest req, Authentication auth) {
        return bookingService.add(tripId, req, null, currentUser.requireId(auth));
    }

    /** 예약 확인증(항공권 e-티켓·숙소 바우처) 첨부 — 이미지 또는 PDF. */
    @PostMapping(path = "/api/bookings/{id}/ticket", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookingResponse attachTicket(@PathVariable Long id,
                                        @RequestPart("file") MultipartFile file,
                                        Authentication auth) {
        return bookingService.attachTicket(id, file, currentUser.requireId(auth));
    }

    @DeleteMapping("/api/bookings/{id}/ticket")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTicket(@PathVariable Long id, Authentication auth) {
        bookingService.removeTicket(id, currentUser.requireId(auth));
    }

    @DeleteMapping("/api/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        bookingService.delete(id, currentUser.requireId(auth));
    }
}
