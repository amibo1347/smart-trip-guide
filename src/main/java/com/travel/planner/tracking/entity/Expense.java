package com.travel.planner.tracking.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지출 기록 (설계 2.3). 간편 가계부.
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

    @Column(name = "client_uuid", nullable = false, length = 64)
    private String clientUuid;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 30)
    private String category;

    @Column(length = 100)
    private String memo;

    @Column(name = "spent_at", nullable = false)
    private LocalDateTime spentAt;

    @Builder
    private Expense(Long tripId, String clientUuid, BigDecimal amount,
                    String category, String memo, LocalDateTime spentAt) {
        this.tripId = tripId;
        this.clientUuid = clientUuid;
        this.amount = amount;
        this.category = category;
        this.memo = memo;
        this.spentAt = spentAt;
    }
}
