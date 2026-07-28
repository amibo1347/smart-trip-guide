package com.travel.planner.expense.service;

import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.expense.dto.ExpenseRequest;
import com.travel.planner.expense.dto.ExpenseResponse;
import com.travel.planner.expense.dto.ExpenseSummaryResponse;
import com.travel.planner.expense.dto.ExpenseSummaryResponse.PayerBalance;
import com.travel.planner.expense.dto.ExpenseSummaryResponse.Transfer;
import com.travel.planner.expense.entity.Expense;
import com.travel.planner.expense.repository.ExpenseRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가계부(지출 내역) CRUD + N빵 정산 계산.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private final TripService tripService;
    private final ExpenseRepository expenseRepository;

    public ExpenseSummaryResponse summary(Long tripId, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        List<Expense> expenses = expenseRepository.findByTripIdOrderBySpentOnDescIdDesc(tripId);

        List<ExpenseResponse> items = expenses.stream().map(ExpenseResponse::from).toList();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal myShare = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> paidByPayer = new LinkedHashMap<>();
        for (Expense e : expenses) {
            total = total.add(e.getAmount());
            int split = e.getSplitCount() == null || e.getSplitCount() < 1 ? 1 : e.getSplitCount();
            myShare = myShare.add(e.getAmount().divide(BigDecimal.valueOf(split), 0, RoundingMode.HALF_UP));
            String cat = (e.getCategory() == null || e.getCategory().isBlank()) ? "기타" : e.getCategory();
            byCategory.merge(cat, e.getAmount(), BigDecimal::add);
            String payer = e.getPayer() == null ? "" : e.getPayer().trim();
            if (!payer.isEmpty()) {
                paidByPayer.merge(payer, e.getAmount(), BigDecimal::add);
            }
        }

        int headcount = Math.max(trip.getHeadcount(), 1);
        BigDecimal perPerson = total.divide(BigDecimal.valueOf(headcount), 0, RoundingMode.HALF_UP);

        // 결제자별 정산 잔액(냄 − 1인당). 결제자를 각각 1인으로 본다.
        List<PayerBalance> byPayer = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> en : paidByPayer.entrySet()) {
            byPayer.add(new PayerBalance(en.getKey(), en.getValue(), en.getValue().subtract(perPerson)));
        }

        List<Transfer> transfers = settle(paidByPayer, perPerson, headcount);

        return new ExpenseSummaryResponse(items, total, byCategory, headcount, perPerson, myShare, byPayer, transfers);
    }

    /**
     * 최소 송금 정산: 각 사람의 잔액(냄−1인당)을 구하고, 이름 없는 동행은 -1인당으로 채워
     * 채무자→채권자로 그리디 매칭한다.
     */
    private List<Transfer> settle(Map<String, BigDecimal> paidByPayer, BigDecimal perPerson, int headcount) {
        if (paidByPayer.isEmpty()) {
            return List.of();
        }
        // 잔액 목록: 결제자 + (인원 − 결제자수) 만큼의 미지정 동행
        List<String> names = new ArrayList<>();
        List<BigDecimal> bal = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> en : paidByPayer.entrySet()) {
            names.add(en.getKey());
            bal.add(en.getValue().subtract(perPerson));
        }
        int extra = headcount - paidByPayer.size();
        for (int i = 0; i < extra; i++) {
            names.add("동행 " + (i + 1));
            bal.add(perPerson.negate());
        }

        List<Transfer> out = new ArrayList<>();
        int guard = 0;
        while (guard++ < 100) {
            int maxCred = -1, maxDebt = -1;
            for (int i = 0; i < bal.size(); i++) {
                if (maxCred < 0 || bal.get(i).compareTo(bal.get(maxCred)) > 0) maxCred = i;
                if (maxDebt < 0 || bal.get(i).compareTo(bal.get(maxDebt)) < 0) maxDebt = i;
            }
            if (maxCred < 0 || maxDebt < 0) break;
            BigDecimal credit = bal.get(maxCred);
            BigDecimal debt = bal.get(maxDebt).negate();
            if (credit.signum() <= 0 || debt.signum() <= 0) break;
            BigDecimal move = credit.min(debt);
            if (move.compareTo(BigDecimal.valueOf(1)) < 0) break; // 1원 미만 잔돈 무시
            out.add(new Transfer(names.get(maxDebt), names.get(maxCred), move));
            bal.set(maxCred, bal.get(maxCred).subtract(move));
            bal.set(maxDebt, bal.get(maxDebt).add(move));
        }
        return out;
    }

    @Transactional
    public ExpenseResponse create(Long tripId, ExpenseRequest req, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        int split = req.splitCount() != null ? req.splitCount() : Math.max(trip.getHeadcount(), 1);
        Expense saved = expenseRepository.save(Expense.builder()
                .tripId(tripId)
                .spentOn(req.spentOn())
                .category(req.category())
                .title(req.title())
                .amount(req.amount())
                .splitCount(split)
                .payer(req.payer())
                .build());
        return ExpenseResponse.from(saved);
    }

    @Transactional
    public ExpenseResponse update(Long expenseId, ExpenseRequest req, Long userId) {
        Expense e = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new NotFoundException("지출 내역을 찾을 수 없습니다: " + expenseId));
        Trip trip = tripService.getOwnedTrip(e.getTripId(), userId);
        int split = req.splitCount() != null ? req.splitCount() : Math.max(trip.getHeadcount(), 1);
        e.update(req.spentOn(), req.category(), req.title(), req.amount(), split, req.payer());
        return ExpenseResponse.from(expenseRepository.save(e));
    }

    @Transactional
    public void delete(Long expenseId, Long userId) {
        Expense e = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new NotFoundException("지출 내역을 찾을 수 없습니다: " + expenseId));
        tripService.getOwnedTrip(e.getTripId(), userId);
        expenseRepository.delete(e);
    }
}
