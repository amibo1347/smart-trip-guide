package com.travel.planner.budget.dto;

import java.math.BigDecimal;

/**
 * 정밀 예산 점검(설계 2.2 확장) + 여행 중 실시간 집행 점검.
 * - 계획 관점: 활동비(예상) + 확정 예약 vs 한도.
 * - 실집행 관점(live*): 확정 예약 + 실제 지출(moments) vs 한도 → 여행 중 초과 경고용.
 */
public record BudgetSummaryResponse(
        BigDecimal budgetLimit,   // 여행에 설정한 예산 한도(없으면 null)
        BigDecimal activityTotal, // 일정 항목 예상비용(estCost) 합계
        BigDecimal bookingTotal,  // 확정 예약(항공/숙소) 가격 합계
        BigDecimal plannedTotal,  // activityTotal + bookingTotal
        BigDecimal remaining,     // budgetLimit - plannedTotal (한도 없으면 null)
        boolean overBudget,       // 한도를 초과했는가(계획 기준)
        BigDecimal overAmount,    // 초과 금액(초과 아닐 때 0)
        // ── 여행 중 실시간 집행 ──
        BigDecimal actualSpent,     // 실제 지출 합계(moments amount)
        BigDecimal liveTotal,       // bookingTotal + actualSpent (실제 집행 금액)
        BigDecimal liveRemaining,   // budgetLimit - liveTotal (한도 없으면 null)
        boolean liveOverBudget,     // 실집행이 한도를 초과했는가
        BigDecimal liveOverAmount,  // 실집행 초과 금액(초과 아닐 때 0)
        Double usedRatio            // liveTotal / budgetLimit * 100 (한도 없거나 0이면 null)
) {
}
