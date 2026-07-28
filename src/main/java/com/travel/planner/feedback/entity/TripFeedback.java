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

    /** 좋았던 점 */
    @Column(length = 1000)
    private String liked;

    /** 아쉬웠던 점 */
    @Column(length = 1000)
    private String regret;

    /** 다음엔 이렇게 */
    @Column(name = "next_time", length = 1000)
    private String nextTime;

    @Builder
    private TripFeedback(Long tripId, Integer overallScore, BigDecimal budgetDiff, String comment,
                         String liked, String regret, String nextTime) {
        this.tripId = tripId;
        this.overallScore = overallScore;
        this.budgetDiff = budgetDiff;
        this.comment = comment;
        this.liked = liked;
        this.regret = regret;
        this.nextTime = nextTime;
    }

    public void update(Integer overallScore, BigDecimal budgetDiff, String comment,
                       String liked, String regret, String nextTime) {
        this.overallScore = overallScore;
        this.budgetDiff = budgetDiff;
        this.comment = comment;
        this.liked = liked;
        this.regret = regret;
        this.nextTime = nextTime;
    }
}
