package com.travel.planner.expense.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가계부 지출 한 건. 여행 지갑에서 직접 입력하는 상세 내역.
 * splitCount(나눠 낼 인원)로 N빵 정산을 지원하고, payer(결제자)로 누가 냈는지도 남긴다.
 */
@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "spent_on")
    private LocalDate spentOn;

    @Column(length = 20)
    private String category;

    @Column(length = 120)
    private String title;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    /** 이 지출을 나눠 내는 인원(N빵). 최소 1. */
    @Column(name = "split_count", nullable = false)
    private Integer splitCount;

    /** 결제자(선택). 누가 냈는지 기록해 정산에 쓴다. */
    @Column(length = 30)
    private String payer;

    @Builder
    private Expense(Long tripId, LocalDate spentOn, String category, String title,
                    BigDecimal amount, Integer splitCount, String payer) {
        this.tripId = tripId;
        this.spentOn = spentOn;
        this.category = category;
        this.title = title;
        this.amount = amount;
        this.splitCount = splitCount == null || splitCount < 1 ? 1 : splitCount;
        this.payer = payer;
    }

    public void update(LocalDate spentOn, String category, String title,
                       BigDecimal amount, Integer splitCount, String payer) {
        this.spentOn = spentOn;
        this.category = category;
        this.title = title;
        this.amount = amount;
        this.splitCount = splitCount == null || splitCount < 1 ? 1 : splitCount;
        this.payer = payer;
    }
}
