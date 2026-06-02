package com.travel.planner.planning.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일자 안의 개별 일정 (설계 2.2 PlanItem). 장소는 선택적 연결.
 */
@Entity
@Table(name = "plan_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_day_id", nullable = false)
    private PlanDay planDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanItemType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "planned_start")
    private LocalTime plannedStart;

    @Column(name = "planned_end")
    private LocalTime plannedEnd;

    @Column(name = "est_cost", precision = 10, scale = 2)
    private BigDecimal estCost;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private PlanItem(Place place, PlanItemType type, String title, LocalTime plannedStart,
                     LocalTime plannedEnd, BigDecimal estCost, int sortOrder) {
        this.place = place;
        this.type = type;
        this.title = title;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.estCost = estCost;
        this.sortOrder = sortOrder;
    }

    void assignDay(PlanDay planDay) {
        this.planDay = planDay;
    }
}
