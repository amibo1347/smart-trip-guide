package com.travel.planner.feedback.entity;

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
 * 계획 항목 vs 실제 (설계 2.4). PlanItem 1:1.
 */
@Entity
@Table(name = "plan_item_actuals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanItemActual extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_item_id", nullable = false)
    private Long planItemId;

    @Column(nullable = false)
    private boolean visited;

    @Column(name = "actual_cost", precision = 10, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    private Integer satisfaction; // 1~5

    @Builder
    private PlanItemActual(Long planItemId, boolean visited, BigDecimal actualCost,
                           LocalDateTime actualStart, Integer satisfaction) {
        this.planItemId = planItemId;
        this.visited = visited;
        this.actualCost = actualCost;
        this.actualStart = actualStart;
        this.satisfaction = satisfaction;
    }

    public void update(boolean visited, BigDecimal actualCost, Integer satisfaction) {
        this.visited = visited;
        this.actualCost = actualCost;
        this.satisfaction = satisfaction;
    }
}
