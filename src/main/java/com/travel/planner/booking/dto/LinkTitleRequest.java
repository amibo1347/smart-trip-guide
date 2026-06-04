package com.travel.planner.booking.dto;

import jakarta.validation.constraints.NotBlank;

/** 예약 페이지 URL에서 이름(제목)만 가져오기 위한 요청. */
public record LinkTitleRequest(@NotBlank String url) {
}
