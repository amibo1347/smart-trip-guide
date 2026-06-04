package com.travel.planner.tracking.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.tracking.dto.MomentRequest;
import com.travel.planner.tracking.dto.MomentResponses.MomentResponse;
import com.travel.planner.tracking.dto.MomentResponses.MomentSummary;
import com.travel.planner.tracking.service.GeocodingService;
import com.travel.planner.tracking.service.PhotoStorageService;
import com.travel.planner.tracking.service.TrackingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 여행 중 통합 기록 API. 사진 포함은 multipart, 사진 없는 빠른 기록은 JSON(오프라인 큐 재전송 호환).
 */
@RestController
@RequestMapping("/api/trips/{tripId}")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final PhotoStorageService photoStorage;
    private final GeocodingService geocoding;
    private final CurrentUser currentUser;

    /** 통합 기록(사진 포함). data 파트=JSON 필드, photo 파트=이미지(선택). */
    @PostMapping(path = "/moments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MomentResponse record(@PathVariable Long tripId,
                                 @RequestPart("data") @Valid MomentRequest data,
                                 @RequestPart(value = "photo", required = false) MultipartFile photo,
                                 Authentication auth) {
        String photoUrl = photoStorage.store(photo);
        // 역지오코딩은 트랜잭션 밖에서(외부 HTTP 가 DB 커넥션을 점유하지 않도록). 실패 시 null.
        String place = geocoding.reverse(data.latitude(), data.longitude());
        return trackingService.record(tripId, data, photoUrl, place, currentUser.requireId(auth));
    }

    /** 통합 기록(사진 없음, JSON) — 오프라인 큐 재전송 경로. */
    @PostMapping(path = "/moments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MomentResponse recordJson(@PathVariable Long tripId,
                                     @Valid @RequestBody MomentRequest data, Authentication auth) {
        String place = geocoding.reverse(data.latitude(), data.longitude());
        return trackingService.record(tripId, data, null, place, currentUser.requireId(auth));
    }

    @GetMapping("/moments")
    public List<MomentResponse> list(@PathVariable Long tripId, Authentication auth) {
        return trackingService.list(tripId, currentUser.requireId(auth));
    }

    @GetMapping("/moments/summary")
    public MomentSummary summary(@PathVariable Long tripId, Authentication auth) {
        return trackingService.summary(tripId, currentUser.requireId(auth));
    }

    /** 기록 완전 삭제. */
    @DeleteMapping("/moments/{momentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long tripId, @PathVariable Long momentId, Authentication auth) {
        trackingService.delete(momentId, currentUser.requireId(auth));
    }
}
