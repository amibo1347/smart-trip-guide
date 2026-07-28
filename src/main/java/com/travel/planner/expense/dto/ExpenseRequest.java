package com.travel.planner.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 가계부 지출 입력. splitCount 미지정 시 서버가 여행 인원수로 채운다. */
public record ExpenseRequest(
        LocalDate spentOn,
        @Size(max = 20) String category,
        @Size(max = 120) String title,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Positive Integer splitCount,
        @Size(max = 30) String payer
) {
}
