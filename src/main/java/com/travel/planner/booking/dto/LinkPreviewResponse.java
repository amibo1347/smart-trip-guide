package com.travel.planner.booking.dto;

/** 링크 미리보기(Open Graph). 가격은 사이트별로 불확실하여 포함하지 않음(사용자 확인 입력). */
public record LinkPreviewResponse(
        String url,
        String title,
        String imageUrl,
        String description,
        String siteName
) {
}
