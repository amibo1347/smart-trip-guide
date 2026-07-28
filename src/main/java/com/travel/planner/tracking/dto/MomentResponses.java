package com.travel.planner.tracking.dto;

import com.travel.planner.tracking.entity.TripMoment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 통합 기록 응답 DTO 묶음. */
public final class MomentResponses {

    private MomentResponses() {
    }

    public record MomentResponse(
            Long id, Long planItemId, String clientUuid, LocalDateTime recordedAt,
            BigDecimal latitude, BigDecimal longitude, Float accuracyM, String place,
            String mood, BigDecimal amount, String category, String memo, String photoUrl) {

        public static MomentResponse from(TripMoment m) {
            return new MomentResponse(m.getId(), m.getPlanItemId(), m.getClientUuid(), m.getRecordedAt(),
                    m.getLatitude(), m.getLongitude(), m.getAccuracyM(), m.getPlace(),
                    m.getMood(), m.getAmount(), m.getCategory(), m.getMemo(), m.getPhotoUrl());
        }
    }

    /** 지출 요약: 합계 + 분류별 합계(금액이 있는 기록만). */
    public record MomentSummary(BigDecimal total, Map<String, BigDecimal> byCategory) {
    }
}
