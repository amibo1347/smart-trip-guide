package com.travel.planner.feedback.service;

import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.feedback.dto.ActualUpsertRequest;
import com.travel.planner.feedback.dto.FeedbackUpsertRequest;
import com.travel.planner.feedback.dto.ReviewResponse;
import com.travel.planner.feedback.dto.ReviewResponse.FeedbackView;
import com.travel.planner.feedback.dto.ReviewResponse.LocationPoint;
import com.travel.planner.feedback.dto.ReviewResponse.ReviewItem;
import com.travel.planner.feedback.entity.PlanItemActual;
import com.travel.planner.feedback.entity.TripFeedback;
import com.travel.planner.feedback.repository.PlanItemActualRepository;
import com.travel.planner.feedback.repository.TripFeedbackRepository;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.repository.PlanItemRepository;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.tracking.entity.TripMoment;
import com.travel.planner.tracking.repository.TripMomentRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 후 복기(설계 2.4): 계획 vs 실제, 예산 vs 지출, 위치 시각화, 회고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final TripService tripService;
    private final PlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final PlanItemActualRepository actualRepository;
    private final TripFeedbackRepository feedbackRepository;
    private final TripMomentRepository momentRepository;

    public ReviewResponse getReview(Long tripId, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);

        // 통합 기록 집계(지출/위치/기분을 한 테이블에서)
        List<TripMoment> moments = momentRepository.findByTripIdOrderByRecordedAtDesc(tripId);
        BigDecimal actualSpent = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (TripMoment m : moments) {
            if (m.getAmount() != null) {
                actualSpent = actualSpent.add(m.getAmount());
                String cat = m.getCategory() == null ? "기타" : m.getCategory();
                byCategory.merge(cat, m.getAmount(), BigDecimal::add);
            }
        }

        // 계획 항목 + 실제(visited/cost/satisfaction)
        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(tripId).orElse(null);
        List<ReviewItem> items = new ArrayList<>();
        BigDecimal plannedTotal = BigDecimal.ZERO;
        int visitedCount = 0;
        if (plan != null) {
            List<Long> itemIds = new ArrayList<>();
            for (PlanDay day : plan.getDays()) {
                for (PlanItem it : day.getItems()) {
                    itemIds.add(it.getId());
                }
            }
            Map<Long, PlanItemActual> actuals = new LinkedHashMap<>();
            if (!itemIds.isEmpty()) {
                actualRepository.findByPlanItemIdIn(itemIds)
                        .forEach(a -> actuals.put(a.getPlanItemId(), a));
            }
            for (PlanDay day : plan.getDays()) {
                for (PlanItem it : day.getItems()) {
                    if (it.getEstCost() != null) {
                        plannedTotal = plannedTotal.add(it.getEstCost());
                    }
                    PlanItemActual a = actuals.get(it.getId());
                    boolean visited = a != null && a.isVisited();
                    if (visited) {
                        visitedCount++;
                    }
                    items.add(new ReviewItem(
                            it.getId(), day.getDayNo(), it.getTitle(), it.getType().name(),
                            it.getEstCost(), visited,
                            a == null ? null : a.getActualCost(),
                            a == null ? null : a.getSatisfaction()));
                }
            }
        }

        // 위치 포인트 (지도용) — 좌표가 있는 기록만. 사진/기분/메모/금액도 함께 실어 지도 일기로.
        List<LocationPoint> locations = moments.stream()
                .filter(m -> m.getLatitude() != null && m.getLongitude() != null)
                .map(m -> new LocationPoint(m.getLatitude(), m.getLongitude(), m.getRecordedAt(),
                        m.getMood(), m.getAmount(), m.getMemo(), m.getPhotoUrl()))
                .toList();
        int moodCount = (int) moments.stream().filter(m -> m.getMood() != null).count();

        BigDecimal budgetDiff = trip.getBudgetLimit() == null ? null
                : trip.getBudgetLimit().subtract(actualSpent);

        FeedbackView feedbackView = feedbackRepository.findByTripId(tripId)
                .map(f -> new FeedbackView(f.getOverallScore(), f.getBudgetDiff(), f.getComment()))
                .orElse(null);

        return new ReviewResponse(
                trip.getBudgetLimit(), plannedTotal, actualSpent, budgetDiff,
                byCategory, locations.size(), moodCount, visitedCount, items.size(),
                items, locations, feedbackView);
    }

    @Transactional
    public void upsertActual(Long planItemId, ActualUpsertRequest req, Long userId) {
        PlanItem item = planItemRepository.findById(planItemId)
                .orElseThrow(() -> new NotFoundException("일정 항목을 찾을 수 없습니다: " + planItemId));
        tripService.getOwnedTrip(item.getPlanDay().getPlan().getTripId(), userId);

        PlanItemActual actual = actualRepository.findByPlanItemId(planItemId)
                .orElseGet(() -> PlanItemActual.builder().planItemId(planItemId).build());
        actual.update(req.visited(), req.actualCost(), req.satisfaction());
        actualRepository.save(actual);
    }

    @Transactional
    public FeedbackView upsertFeedback(Long tripId, FeedbackUpsertRequest req, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);

        BigDecimal actualSpent = momentRepository.findByTripIdOrderByRecordedAtDesc(tripId).stream()
                .map(TripMoment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal budgetDiff = trip.getBudgetLimit() == null ? null
                : trip.getBudgetLimit().subtract(actualSpent);

        TripFeedback feedback = feedbackRepository.findByTripId(tripId)
                .orElseGet(() -> TripFeedback.builder().tripId(tripId).build());
        feedback.update(req.overallScore(), budgetDiff, req.comment());
        TripFeedback saved = feedbackRepository.save(feedback);
        return new FeedbackView(saved.getOverallScore(), saved.getBudgetDiff(), saved.getComment());
    }
}
