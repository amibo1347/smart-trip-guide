package com.travel.planner.planning.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI/사용자가 만든 일정 1버전 (설계 2.2 Plan). Trip:Plan = 1:N (재생성 이력 보관).
 */
@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by", nullable = false, length = 10)
    private GeneratedBy generatedBy;

    @Column(name = "ai_model", length = 50)
    private String aiModel;

    @Column(name = "destination_city", length = 100)
    private String destinationCity;

    @Column(name = "destination_lat", precision = 10, scale = 7)
    private java.math.BigDecimal destinationLat;

    @Column(name = "destination_lng", precision = 10, scale = 7)
    private java.math.BigDecimal destinationLng;

    @Column(name = "prompt_snapshot", columnDefinition = "json")
    private String promptSnapshot;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNo ASC")
    private List<PlanDay> days = new ArrayList<>();

    @Builder
    private Plan(Long tripId, Integer version, GeneratedBy generatedBy, String aiModel,
                 String destinationCity, java.math.BigDecimal destinationLat,
                 java.math.BigDecimal destinationLng, String promptSnapshot) {
        this.tripId = tripId;
        this.version = version;
        this.generatedBy = generatedBy == null ? GeneratedBy.USER : generatedBy;
        this.aiModel = aiModel;
        this.destinationCity = destinationCity;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.promptSnapshot = promptSnapshot;
    }

    public void addDay(PlanDay day) {
        days.add(day);
        day.assignPlan(this);
    }
}
