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
 * 여행 중 '한 순간'의 통합 기록(설계 2.3 개정).
 * 언제(recordedAt) · 어디서(lat/lng) · 어떤 기분(mood) · 얼마(amount) · 무엇을(memo/photo).
 * 위치·기분·금액·메모·사진은 모두 선택 — 빠른 기록을 위한 단일 진입점.
 */
@Entity
@Table(name = "trip_moments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMoment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "client_uuid", nullable = false, length = 64)
    private String clientUuid;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "accuracy_m")
    private Float accuracyM;

    @Column(length = 120)
    private String place; // 좌표 역지오코딩 결과(행정구역명, 선택)

    @Column(length = 16)
    private String mood;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 30)
    private String category;

    @Column(length = 200)
    private String memo;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Builder
    private TripMoment(Long tripId, String clientUuid, LocalDateTime recordedAt,
                       BigDecimal latitude, BigDecimal longitude, Float accuracyM, String place,
                       String mood, BigDecimal amount, String category, String memo, String photoUrl) {
        this.tripId = tripId;
        this.clientUuid = clientUuid;
        this.recordedAt = recordedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyM = accuracyM;
        this.place = place;
        this.mood = mood;
        this.amount = amount;
        this.category = category;
        this.memo = memo;
        this.photoUrl = photoUrl;
    }
}
