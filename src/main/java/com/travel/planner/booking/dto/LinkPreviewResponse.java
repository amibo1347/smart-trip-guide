package com.travel.planner.booking.dto;

import java.math.BigDecimal;

/**
 * 링크 미리보기(Open Graph + 구조화 데이터).
 * price 는 사이트가 메타/JSON-LD로 '실제 노출한 값'만 채움(없으면 null, 환각 없음).
 */
public record LinkPreviewResponse(
        String url,
        String title,
        String imageUrl,
        String description,
        String siteName,
        BigDecimal price,
        String currency
) {
}
