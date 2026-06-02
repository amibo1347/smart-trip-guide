package com.travel.planner.planning.dto;

import jakarta.validation.constraints.Size;

/**
 * AI 일정 생성 요청.
 * note: 지역/요청사항(선택). origin: 출발지(선택, 기본 서울).
 * flightTime: 항공 선호 시간대(MORNING/AFTERNOON/EVENING/ANY). lowCost: 저가항공 선호.
 */
public record GeneratePlanRequest(
        @Size(max = 500) String note,
        @Size(max = 50) String origin,
        @Size(max = 20) String flightTime,
        Boolean lowCost
) {
}
