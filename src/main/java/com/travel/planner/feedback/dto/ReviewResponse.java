package com.travel.planner.feedback.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 복기 화면용 집계 응답: 예산/지출, 계획 vs 실제, 위치, 회고.
 */
public record ReviewResponse(
        BigDecimal budgetLimit,
        BigDecimal plannedCostTotal,
        BigDecimal actualSpent,
        BigDecimal budgetDiff,                 // budgetLimit - actualSpent (예산 없으면 null)
        Map<String, BigDecimal> expenseByCategory,
        int locationCount,
        int moodCount,
        int visitedCount,
        int totalItems,
        List<ReviewItem> items,
        List<LocationPoint> locations,
        FeedbackView feedback                  // 없으면 null
) {
    public record ReviewItem(
            Long planItemId, int dayNo, String title, String type,
            BigDecimal estCost, boolean visited, BigDecimal actualCost, Integer satisfaction) {
    }

    public record LocationPoint(BigDecimal latitude, BigDecimal longitude, LocalDateTime recordedAt,
                                String mood, BigDecimal amount, String memo, String photoUrl) {
    }

    public record FeedbackView(Integer overallScore, BigDecimal budgetDiff, String comment,
                               String liked, String regret, String nextTime) {
    }
}
