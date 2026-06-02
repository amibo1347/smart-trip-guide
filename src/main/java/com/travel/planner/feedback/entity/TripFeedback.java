package com.travel.planner.feedback.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 단위 회고 (설계 2.4). Trip 1:1.
 */
@Entity
@Table(name = "trip_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "overall_score")
    private Integer overallScore; // 1~5

    @Column(name = "budget_diff", precision = 12, scale = 2)
    private BigDecimal budgetDiff;

    @Column(length = 2000)
    private String comment;

    @Builder
    private TripFeedback(Long tripId, Integer overallScore, BigDecimal budgetDiff, String comment) {
        this.tripId = tripId;
        this.overallScore = overallScore;
        this.budgetDiff = budgetDiff;
        this.comment = comment;
    }

    public void update(Integer overallScore, BigDecimal budgetDiff, String comment) {
        this.overallScore = overallScore;
        this.budgetDiff = budgetDiff;
        this.comment = comment;
    }
}
