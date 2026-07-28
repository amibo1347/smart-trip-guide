package com.travel.planner.trip.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 1건 = 계획·기록·복기·피드백 전 과정의 컨테이너(루트 애그리거트, 설계 5.1).
 * 사용자는 식별자(userId)로만 참조 — 애그리거트 경계 분리.
 */
@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    /** 목적지 이름(예: "오사카", "부산 해운대"). 인기/주변 장소 탐색의 기준점 라벨. */
    @Column(name = "destination_name", length = 120)
    private String destinationName;

    /** 목적지 대표 좌표. 인기 장소·주변 장소를 이 좌표 기준으로 찾는다. null 이면 탐색 비활성. */
    @Column(name = "destination_lat", precision = 10, scale = 7)
    private BigDecimal destinationLat;

    @Column(name = "destination_lng", precision = 10, scale = 7)
    private BigDecimal destinationLng;

    /** 목적지 대표 사진 URL. 여행 카드/요약의 표지. */
    @Column(name = "destination_photo", length = 1000)
    private String destinationPhoto;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int headcount;

    @Column(name = "budget_limit", precision = 12, scale = 2)
    private BigDecimal budgetLimit;

    @Column(length = 50)
    private String concept;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status;

    /** 읽기 전용 공유 토큰. null 이면 비공개. 발급 시 /share/{token} 으로 누구나 일정 열람. */
    @Column(name = "share_token", length = 64)
    private String shareToken;

    @Builder
    private Trip(Long userId, String title, String destinationName,
                BigDecimal destinationLat, BigDecimal destinationLng, String destinationPhoto,
                LocalDate startDate, LocalDate endDate,
                int headcount, BigDecimal budgetLimit, String concept) {
        validatePeriod(startDate, endDate);
        this.userId = userId;
        this.title = title;
        this.destinationName = destinationName;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.destinationPhoto = destinationPhoto;
        this.startDate = startDate;
        this.endDate = endDate;
        this.headcount = headcount;
        this.budgetLimit = budgetLimit;
        this.concept = concept;
        this.status = TripStatus.PLANNED;
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    public void changeStatus(TripStatus status) {
        this.status = status;
    }

    /** 예산 한도 설정/변경(가계부에서 직접 조정). null 이면 한도 없음. */
    public void changeBudgetLimit(BigDecimal budgetLimit) {
        this.budgetLimit = budgetLimit;
    }

    /** 공유 활성화: 토큰 지정. */
    public void enableShare(String token) {
        this.shareToken = token;
    }

    /** 공유 중단: 토큰 폐기. */
    public void disableShare() {
        this.shareToken = null;
    }
}
