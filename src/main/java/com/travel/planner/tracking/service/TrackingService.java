package com.travel.planner.tracking.service;

import com.travel.planner.tracking.dto.ExpenseRequest;
import com.travel.planner.tracking.dto.LocationRequest;
import com.travel.planner.tracking.dto.MoodRequest;
import com.travel.planner.tracking.dto.TrackingResponses.ExpenseResponse;
import com.travel.planner.tracking.dto.TrackingResponses.ExpenseSummary;
import com.travel.planner.tracking.dto.TrackingResponses.LocationResponse;
import com.travel.planner.tracking.dto.TrackingResponses.MoodResponse;
import com.travel.planner.tracking.entity.Expense;
import com.travel.planner.tracking.entity.LocationLog;
import com.travel.planner.tracking.entity.MoodLog;
import com.travel.planner.tracking.repository.ExpenseRepository;
import com.travel.planner.tracking.repository.LocationLogRepository;
import com.travel.planner.tracking.repository.MoodLogRepository;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 중 기록 서비스. 모든 쓰기는 client_uuid 멱등 처리 —
 * 같은 (trip, clientUuid) 재전송은 기존 레코드를 그대로 반환(오프라인 큐 재시도 대비, 설계 4.B).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackingService {

    private final LocationLogRepository locationRepo;
    private final MoodLogRepository moodRepo;
    private final ExpenseRepository expenseRepo;
    private final TripService tripService;

    // ── 위치 ──
    @Transactional
    public LocationResponse recordLocation(Long tripId, LocationRequest req) {
        tripService.getEntity(tripId);
        LocationLog saved = locationRepo.findByTripIdAndClientUuid(tripId, req.clientUuid())
                .orElseGet(() -> locationRepo.save(LocationLog.builder()
                        .tripId(tripId)
                        .clientUuid(req.clientUuid())
                        .recordedAt(req.recordedAt())
                        .latitude(req.latitude())
                        .longitude(req.longitude())
                        .accuracyM(req.accuracyM())
                        .build()));
        return LocationResponse.from(saved);
    }

    public List<LocationResponse> listLocations(Long tripId) {
        return locationRepo.findByTripIdOrderByRecordedAtDesc(tripId).stream()
                .map(LocationResponse::from).toList();
    }

    // ── 기분 ──
    @Transactional
    public MoodResponse recordMood(Long tripId, MoodRequest req) {
        tripService.getEntity(tripId);
        MoodLog saved = moodRepo.findByTripIdAndClientUuid(tripId, req.clientUuid())
                .orElseGet(() -> moodRepo.save(MoodLog.builder()
                        .tripId(tripId)
                        .clientUuid(req.clientUuid())
                        .locationLogId(req.locationLogId())
                        .emoji(req.emoji())
                        .recordedAt(req.recordedAt())
                        .build()));
        return MoodResponse.from(saved);
    }

    public List<MoodResponse> listMoods(Long tripId) {
        return moodRepo.findByTripIdOrderByRecordedAtDesc(tripId).stream()
                .map(MoodResponse::from).toList();
    }

    // ── 지출 ──
    @Transactional
    public ExpenseResponse recordExpense(Long tripId, ExpenseRequest req) {
        tripService.getEntity(tripId);
        Expense saved = expenseRepo.findByTripIdAndClientUuid(tripId, req.clientUuid())
                .orElseGet(() -> expenseRepo.save(Expense.builder()
                        .tripId(tripId)
                        .clientUuid(req.clientUuid())
                        .amount(req.amount())
                        .category(req.category())
                        .memo(req.memo())
                        .spentAt(req.spentAt())
                        .build()));
        return ExpenseResponse.from(saved);
    }

    public List<ExpenseResponse> listExpenses(Long tripId) {
        return expenseRepo.findByTripIdOrderBySpentAtDesc(tripId).stream()
                .map(ExpenseResponse::from).toList();
    }

    public ExpenseSummary expenseSummary(Long tripId) {
        List<Expense> expenses = expenseRepo.findByTripIdOrderBySpentAtDesc(tripId);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Expense e : expenses) {
            total = total.add(e.getAmount());
            String cat = e.getCategory() == null ? "기타" : e.getCategory();
            byCategory.merge(cat, e.getAmount(), BigDecimal::add);
        }
        return new ExpenseSummary(total, byCategory);
    }
}
