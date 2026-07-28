package com.travel.planner.expense.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 가계부 + 정산 요약.
 *  - total: 총 지출, byCategory: 항목별 합계
 *  - headcount/perPerson: 균등 N빵(총÷인원)
 *  - myShare: 각 지출을 splitCount 로 나눈 1인 몫의 합(부분 N빵 반영)
 *  - byPayer: 결제자별 낸 금액과 정산 잔액(냄−1인당)
 *  - transfers: 누가 누구에게 보내면 정산이 끝나는지(최소 송금)
 */
public record ExpenseSummaryResponse(
        List<ExpenseResponse> items,
        BigDecimal total,
        Map<String, BigDecimal> byCategory,
        int headcount,
        BigDecimal perPerson,
        BigDecimal myShare,
        List<PayerBalance> byPayer,
        List<Transfer> transfers
) {
    public record PayerBalance(String payer, BigDecimal paid, BigDecimal net) {}

    public record Transfer(String from, String to, BigDecimal amount) {}
}
