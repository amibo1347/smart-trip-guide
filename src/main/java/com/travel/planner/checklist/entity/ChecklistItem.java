package com.travel.planner.checklist.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 준비물 체크리스트 항목. 여행 단위로 쌓이며 담당자를 지정해 여러 명이 나눠 챙길 수 있다.
 */
@Entity
@Table(name = "trip_checklist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChecklistCategory category;

    @Column(length = 50)
    private String assignee;

    @Column(nullable = false)
    private boolean checked;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private ChecklistItem(Long tripId, String title, ChecklistCategory category, String assignee, int sortOrder) {
        this.tripId = tripId;
        this.title = title;
        this.category = category == null ? ChecklistCategory.ETC : category;
        this.assignee = assignee;
        this.sortOrder = sortOrder;
        this.checked = false;
    }

    /** 부분 수정 — null 인 필드는 기존 값을 유지한다(체크만 토글하는 호출이 대부분). */
    public void update(String title, ChecklistCategory category, String assignee, Boolean checked) {
        if (title != null && !title.isBlank()) this.title = title;
        if (category != null) this.category = category;
        if (assignee != null) this.assignee = assignee.isBlank() ? null : assignee;
        if (checked != null) this.checked = checked;
    }
}
