package com.travel.planner.tracking.service;

import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.tracking.dto.MomentRequest;
import com.travel.planner.tracking.dto.MomentResponses.MomentResponse;
import com.travel.planner.tracking.dto.MomentResponses.MomentSummary;
import com.travel.planner.tracking.entity.TripMoment;
import com.travel.planner.tracking.repository.TripMomentRepository;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 중 통합 기록 서비스. 한 번의 기록에 위치·기분·금액·메모·사진을 함께 담는다(모두 선택).
 * 모든 쓰기는 client_uuid 멱등 처리 — 같은 (trip, clientUuid) 재전송은 기존 레코드를 반환(오프라인 큐 대비, 설계 4.B).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackingService {

    private final TripMomentRepository momentRepo;
    private final PhotoStorageService photoStorage;
    private final GeocodingService geocoding;
    private final TripService tripService;

    @Transactional
    public MomentResponse record(Long tripId, MomentRequest req, String photoUrl, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        // 멱등: 같은 (trip, clientUuid) 재전송이면 기존 레코드 반환(지오코딩 재호출 안 함).
        TripMoment existing = momentRepo.findByTripIdAndClientUuid(tripId, req.clientUuid()).orElse(null);
        if (existing != null) {
            return MomentResponse.from(existing);
        }
        // 좌표가 있으면 저장 시점에 지역명으로 변환(실패 시 null → 좌표/생략 폴백).
        String place = geocoding.reverse(req.latitude(), req.longitude());
        TripMoment saved = momentRepo.save(TripMoment.builder()
                .tripId(tripId)
                .clientUuid(req.clientUuid())
                .recordedAt(req.recordedAt())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .accuracyM(req.accuracyM())
                .place(place)
                .mood(blankToNull(req.mood()))
                .amount(req.amount())
                .category(blankToNull(req.category()))
                .memo(blankToNull(req.memo()))
                .photoUrl(photoUrl)
                .build());
        return MomentResponse.from(saved);
    }

    public List<MomentResponse> list(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        return momentRepo.findByTripIdOrderByRecordedAtDesc(tripId).stream()
                .map(MomentResponse::from).toList();
    }

    /** 기록 완전 삭제(사진 파일까지 디스크에서 제거). */
    @Transactional
    public void delete(Long momentId, Long userId) {
        TripMoment m = momentRepo.findById(momentId)
                .orElseThrow(() -> new NotFoundException("기록을 찾을 수 없습니다: " + momentId));
        tripService.getOwnedTrip(m.getTripId(), userId); // 소유권 검증
        photoStorage.delete(m.getPhotoUrl());
        momentRepo.delete(m);
    }

    /** 지출 요약: 금액이 있는 기록만 합산. */
    public MomentSummary summary(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (TripMoment m : momentRepo.findByTripIdOrderByRecordedAtDesc(tripId)) {
            if (m.getAmount() != null) {
                total = total.add(m.getAmount());
                String cat = m.getCategory() == null ? "기타" : m.getCategory();
                byCategory.merge(cat, m.getAmount(), BigDecimal::add);
            }
        }
        return new MomentSummary(total, byCategory);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
