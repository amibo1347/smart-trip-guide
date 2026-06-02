package com.travel.planner.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 일정 항목에 붙일 장소(수동입력). 선택 사항.
 */
public record PlaceInput(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 50) String category,
        @Size(max = 255) String address,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
