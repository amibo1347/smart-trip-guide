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
 * 위치 기록 (설계 2.3). 기록 버튼 1탭 = 시각 + GPS 1건.
 * client_uuid 멱등키로 오프라인 재전송 중복 방지.
 */
@Entity
@Table(name = "location_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "client_uuid", nullable = false, length = 64)
    private String clientUuid;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "accuracy_m")
    private Float accuracyM;

    @Column(name = "place_id")
    private Long placeId;

    @Builder
    private LocationLog(Long tripId, String clientUuid, LocalDateTime recordedAt,
                        BigDecimal latitude, BigDecimal longitude, Float accuracyM) {
        this.tripId = tripId;
        this.clientUuid = clientUuid;
        this.recordedAt = recordedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyM = accuracyM;
    }
}
