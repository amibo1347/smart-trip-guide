package com.travel.planner.tracking.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기분 기록 (설계 2.3). 이모지 1탭.
 */
@Entity
@Table(name = "mood_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoodLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "client_uuid", nullable = false, length = 64)
    private String clientUuid;

    @Column(name = "location_log_id")
    private Long locationLogId;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Builder
    private MoodLog(Long tripId, String clientUuid, Long locationLogId,
                    String emoji, LocalDateTime recordedAt) {
        this.tripId = tripId;
        this.clientUuid = clientUuid;
        this.locationLogId = locationLogId;
        this.emoji = emoji;
        this.recordedAt = recordedAt;
    }
}
