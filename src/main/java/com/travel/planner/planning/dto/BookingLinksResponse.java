package com.travel.planner.planning.dto;

import java.util.List;

/**
 * 항공/숙소 예약 핸드오프 링크. 실제 빈좌석·가격·예약은 각 사이트에서 확인(설계 4.E 회피, 환각 없음).
 */
public record BookingLinksResponse(
        String destination,
        String origin,
        List<Link> flights,
        List<Link> hotels
) {
    public record Link(String label, String url, boolean prefilled) {
    }
}
