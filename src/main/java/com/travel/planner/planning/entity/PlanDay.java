package com.travel.planner.planning.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일자별 일정 (설계 2.2 PlanDay). Trip 기간으로부터 자동 생성.
 */
@Entity
@Table(name = "plan_days")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanDay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "day_no", nullable = false)
    private Integer dayNo;

    @Column(name = "plan_date", nullable = false)
    private LocalDate date;

    @OneToMany(mappedBy = "planDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<PlanItem> items = new ArrayList<>();

    @Builder
    private PlanDay(Integer dayNo, LocalDate date) {
        this.dayNo = dayNo;
        this.date = date;
    }

    void assignPlan(Plan plan) {
        this.plan = plan;
    }

    public void addItem(PlanItem item) {
        items.add(item);
        item.assignDay(this);
    }
}
