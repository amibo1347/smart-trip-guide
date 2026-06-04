package com.travel.planner.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.planner.common.exception.AiException;
import com.travel.planner.feedback.dto.ReviewReportResponse;
import com.travel.planner.feedback.dto.ReviewResponse;
import com.travel.planner.feedback.dto.ReviewResponse.ReviewItem;
import com.travel.planner.planning.ai.GeminiClient;
import com.travel.planner.tracking.entity.TripMoment;
import com.travel.planner.tracking.repository.TripMomentRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 여행 회고 리포트 생성. 복기 집계(ReviewService) + 기록(moments)을 근거 데이터로 프롬프트에 넣고
 * Gemini 구조화 출력으로 요약을 만든다. 수치는 모두 DB 값 — 모델은 해석/문장화만 담당(환각 최소화).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewReportService {

    private static final Map<String, String> LANG_NAMES = Map.of(
            "ko", "Korean (한국어)",
            "en", "English",
            "ja", "Japanese (日本語)",
            "zh", "Simplified Chinese (简体中文)");

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "title", Map.of("type", "string"),
                    "highlights", Map.of("type", "array", "items", Map.of("type", "string")),
                    "spending", Map.of("type", "string"),
                    "mood", Map.of("type", "string"),
                    "tips", Map.of("type", "array", "items", Map.of("type", "string"))),
            "required", List.of("title", "highlights", "spending", "mood", "tips"));

    private final TripService tripService;
    private final ReviewService reviewService;
    private final TripMomentRepository momentRepository;
    private final GeminiClient gemini;
    private final ObjectMapper objectMapper;

    public ReviewReportResponse generate(Long tripId, Long userId, String lang) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        ReviewResponse r = reviewService.getReview(tripId, userId);
        List<TripMoment> moments = momentRepository.findByTripIdOrderByRecordedAtDesc(tripId);

        boolean noData = moments.isEmpty() && r.visitedCount() == 0 && r.feedback() == null;
        if (noData) {
            throw new IllegalArgumentException("아직 회고할 기록이 부족해요. 일정 방문·지출·기분을 남긴 뒤 다시 시도하세요.");
        }

        String prompt = buildPrompt(trip, r, moments, langName(lang));
        String json = gemini.generateJson(prompt, SCHEMA);
        try {
            return objectMapper.readValue(json, ReviewReportResponse.class);
        } catch (Exception e) {
            throw new AiException("회고 리포트 생성 결과를 해석하지 못했습니다. 다시 시도해 주세요.", e);
        }
    }

    private String buildPrompt(Trip trip, ReviewResponse r, List<TripMoment> moments, String langName) {
        long nights = (trip.getStartDate() != null && trip.getEndDate() != null)
                ? Math.max(0, ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate())) : 0;

        // 기분 분포
        Map<String, Integer> moodCounts = new LinkedHashMap<>();
        for (TripMoment m : moments) {
            if (m.getMood() != null) {
                moodCounts.merge(m.getMood(), 1, Integer::sum);
            }
        }
        // 인상적 메모(최대 8개)
        List<String> memos = moments.stream()
                .map(TripMoment::getMemo)
                .filter(s -> s != null && !s.isBlank())
                .limit(8).toList();
        // 만족도 평균
        double avgSat = r.items().stream()
                .map(ReviewItem::satisfaction)
                .filter(s -> s != null && s > 0)
                .mapToInt(Integer::intValue).average().orElse(0);

        StringBuilder b = new StringBuilder();
        b.append("You are a warm, insightful travel journaling assistant. ");
        b.append("Write a short retrospective report of the trip using ONLY the facts below. ");
        b.append("Do not invent places, amounts, or events not present in the data. ");
        b.append("Write everything in ").append(langName).append(". Keep each field concise.\n\n");

        b.append("[Trip]\n");
        b.append("- Title: ").append(nv(trip.getTitle())).append('\n');
        b.append("- Dates: ").append(nv(trip.getStartDate())).append(" ~ ").append(nv(trip.getEndDate()))
                .append(" (").append(nights).append(" nights)\n");
        b.append("- Travelers: ").append(trip.getHeadcount()).append('\n');
        if (trip.getConcept() != null) {
            b.append("- Concept: ").append(trip.getConcept()).append('\n');
        }

        b.append("\n[Budget & Spending]\n");
        b.append("- Budget limit: ").append(r.budgetLimit() == null ? "not set" : krw(r.budgetLimit())).append('\n');
        b.append("- Actual spent: ").append(krw(r.actualSpent())).append('\n');
        if (r.budgetDiff() != null) {
            boolean saved = r.budgetDiff().signum() >= 0;
            b.append("- Budget result: ").append(saved ? "under budget by " : "OVER budget by ")
                    .append(krw(r.budgetDiff().abs())).append('\n');
        }
        if (!r.expenseByCategory().isEmpty()) {
            b.append("- By category: ");
            r.expenseByCategory().forEach((k, v) -> b.append(k).append('=').append(krw(v)).append("  "));
            b.append('\n');
        }

        b.append("\n[Plan vs Actual]\n");
        b.append("- Visited ").append(r.visitedCount()).append('/').append(r.totalItems()).append(" planned items\n");
        if (avgSat > 0) {
            b.append("- Average satisfaction: ").append(String.format("%.1f", avgSat)).append("/5\n");
        }

        if (!moodCounts.isEmpty()) {
            b.append("\n[Moods logged]\n- ");
            moodCounts.forEach((k, v) -> b.append(k).append("x").append(v).append("  "));
            b.append('\n');
        }
        if (!memos.isEmpty()) {
            b.append("\n[Memorable notes]\n");
            memos.forEach(s -> b.append("- ").append(s).append('\n'));
        }
        if (r.feedback() != null && r.feedback().comment() != null && !r.feedback().comment().isBlank()) {
            b.append("\n[Traveler's own note]\n- ").append(r.feedback().comment()).append('\n');
            if (r.feedback().overallScore() != null) {
                b.append("- Overall score: ").append(r.feedback().overallScore()).append("/5\n");
            }
        }

        b.append("\nReturn JSON with: title (one warm sentence summarizing the trip), ");
        b.append("highlights (2-4 short bullet strings of memorable moments grounded in the data), ");
        b.append("spending (one short paragraph interpreting the budget/spending), ");
        b.append("mood (one short paragraph on the emotional tone & satisfaction), ");
        b.append("tips (2-4 short, actionable suggestions for the next trip).");
        return b.toString();
    }

    private static String langName(String lang) {
        return LANG_NAMES.getOrDefault(lang == null ? "ko" : lang, LANG_NAMES.get("ko"));
    }

    private static String krw(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).toBigInteger() + " KRW";
    }

    private static String nv(Object o) {
        return o == null ? "-" : o.toString();
    }
}
