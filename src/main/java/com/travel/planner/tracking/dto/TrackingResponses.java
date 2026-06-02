package com.travel.planner.tracking.dto;

import com.travel.planner.tracking.entity.Expense;
import com.travel.planner.tracking.entity.LocationLog;
import com.travel.planner.tracking.entity.MoodLog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Tracking 응답 DTO 묶음. */
public final class TrackingResponses {

    private TrackingResponses() {
    }

    public record LocationResponse(
            Long id, String clientUuid, LocalDateTime recordedAt,
            BigDecimal latitude, BigDecimal longitude, Float accuracyM) {
        public static LocationResponse from(LocationLog l) {
            return new LocationResponse(l.getId(), l.getClientUuid(), l.getRecordedAt(),
                    l.getLatitude(), l.getLongitude(), l.getAccuracyM());
        }
    }

    public record MoodResponse(
            Long id, String clientUuid, String emoji, LocalDateTime recordedAt, Long locationLogId) {
        public static MoodResponse from(MoodLog m) {
            return new MoodResponse(m.getId(), m.getClientUuid(), m.getEmoji(),
                    m.getRecordedAt(), m.getLocationLogId());
        }
    }

    public record ExpenseResponse(
            Long id, String clientUuid, BigDecimal amount, String category,
            String memo, LocalDateTime spentAt) {
        public static ExpenseResponse from(Expense e) {
            return new ExpenseResponse(e.getId(), e.getClientUuid(), e.getAmount(),
                    e.getCategory(), e.getMemo(), e.getSpentAt());
        }
    }

    /** 지출 요약: 합계 + 카테고리별 합계. */
    public record ExpenseSummary(BigDecimal total, Map<String, BigDecimal> byCategory) {
    }
}
