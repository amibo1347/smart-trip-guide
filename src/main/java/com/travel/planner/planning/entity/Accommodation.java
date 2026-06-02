package com.travel.planner.planning.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 숙박 예약 (설계 2.2 Accommodation). 여행 단위로 매달리며 장소는 선택적 연결.
 */
@Entity
@Table(name = "accommodations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Accommodation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @Builder
    private Accommodation(Long tripId, Place place, String name,
                          LocalDate checkIn, LocalDate checkOut, BigDecimal cost) {
        if (checkIn != null && checkOut != null && checkOut.isBefore(checkIn)) {
            throw new IllegalArgumentException("체크아웃은 체크인보다 빠를 수 없습니다.");
        }
        this.tripId = tripId;
        this.place = place;
        this.name = name;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.cost = cost;
    }
}
