package com.travel.planner.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 마이페이지 개요 — 누적 방문 지도용 장소 + 여행별 요약 카드.
 */
public record MyOverview(
        List<VisitedPlace> places,
        List<TripSummary> trips) {

    /** 누적 지도에 찍을 방문 장소. */
    public record VisitedPlace(String name, BigDecimal latitude, BigDecimal longitude, String tripTitle) {
    }

    /**
     * 여행 요약 카드 — 어디를·언제·얼마나 다녀왔는지 한 장으로 복기.
     *
     * @param phase        UPCOMING / ONGOING / PAST
     * @param dayCount     여행 일수
     * @param placeCount   좌표가 있는 방문 장소 수
     * @param photoCount   기록 사진 수
     * @param totalSpent   실제 지출 합계
     * @param coverPhotoUrl 대표 사진(가장 최근 기록 사진, 없으면 null)
     */
    public record TripSummary(
            Long tripId,
            String title,
            String destinationName,
            LocalDate startDate,
            LocalDate endDate,
            String phase,
            long dayCount,
            int placeCount,
            int photoCount,
            BigDecimal totalSpent,
            String coverPhotoUrl) {
    }
}
