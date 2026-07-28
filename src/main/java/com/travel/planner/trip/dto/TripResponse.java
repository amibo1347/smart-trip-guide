package com.travel.planner.trip.dto;

import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.entity.TripStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TripResponse(
        Long id,
        Long userId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int headcount,
        BigDecimal budgetLimit,
        String concept,
        TripStatus status,
        LocalDateTime createdAt,
        // 공유 중이면 토큰(→ /share/{token}), 비공개면 null. 화면에서 '공유 중' 표시·재발급 생략에 쓴다.
        String shareToken,
        // 목적지 — 있으면 장소 탐색(인기/주변)을 이 좌표 기준으로 켠다.
        String destinationName,
        BigDecimal destinationLat,
        BigDecimal destinationLng,
        String destinationPhoto
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getUserId(),
                trip.getTitle(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getHeadcount(),
                trip.getBudgetLimit(),
                trip.getConcept(),
                trip.getStatus(),
                trip.getCreatedAt(),
                trip.getShareToken(),
                trip.getDestinationName(),
                trip.getDestinationLat(),
                trip.getDestinationLng(),
                trip.getDestinationPhoto()
        );
    }
}
