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

    /** 확정 예약에서 자동 생성된 항목이면 그 예약 id(아니면 null). 예약 삭제 시 정리에 사용. */
    @Column(name = "booking_id")
    private Long bookingId;

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
    private PlanItem(Place place, Long bookingId, PlanItemType type, String title, LocalTime plannedStart,
                     LocalTime plannedEnd, BigDecimal estCost, int sortOrder) {
        this.place = place;
        this.bookingId = bookingId;
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

    /** 장소 연결(수동 위치 지정 시 장소가 없던 항목에 새로 붙이는 용도). */
    public void changePlace(Place place) {
        this.place = place;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** AI 추정 등으로 예상비용만 갱신. */
    public void changeEstCost(BigDecimal estCost) {
        this.estCost = estCost;
    }

    /** 편집(제목/시간/예상비용). 제목은 비어 있으면 기존 값 유지. */
    public void update(String title, LocalTime plannedStart, LocalTime plannedEnd, BigDecimal estCost) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.estCost = estCost;
    }
}
