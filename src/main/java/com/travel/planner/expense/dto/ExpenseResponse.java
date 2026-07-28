package com.travel.planner.expense.dto;

import com.travel.planner.expense.entity.Expense;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** 지출 한 건. perHead = 이 지출을 splitCount 로 나눈 1인 몫. */
public record ExpenseResponse(
        Long id,
        LocalDate spentOn,
        String category,
        String title,
        BigDecimal amount,
        Integer splitCount,
        String payer,
        BigDecimal perHead
) {
    public static ExpenseResponse from(Expense e) {
        int split = e.getSplitCount() == null || e.getSplitCount() < 1 ? 1 : e.getSplitCount();
        BigDecimal perHead = e.getAmount().divide(BigDecimal.valueOf(split), 0, RoundingMode.HALF_UP);
        return new ExpenseResponse(e.getId(), e.getSpentOn(), e.getCategory(), e.getTitle(),
                e.getAmount(), split, e.getPayer(), perHead);
    }
}
