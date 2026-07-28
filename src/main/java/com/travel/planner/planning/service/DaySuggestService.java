package com.travel.planner.planning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.planner.common.exception.AiException;
import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.planning.ai.GeminiClient;
import com.travel.planner.planning.dto.DaySuggestion;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.entity.PlanItemType;
import com.travel.planner.planning.repository.PlanDayRepository;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * '이 날 AI 추천' — 하루치 장소 몇 곳을 제안한다(플랜을 바꾸지 않음).
 *
 * <p>AI 를 전체 일정 생성이 아니라 '담는 걸 돕는 부기능'으로 쓰는 축. 목적지와 이미 담긴 장소를
 * 프롬프트에 넣어, 그 날에 어울리고 중복되지 않는 장소를 추천만 한다. 담는 건 사용자가 직접 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DaySuggestService {

    private final GeminiClient gemini;
    private final TripService tripService;
    private final PlanRepository planRepository;
    private final PlanDayRepository planDayRepository;
    private final ObjectMapper objectMapper;

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("suggestions", Map.of(
                    "type", "ARRAY",
                    "items", Map.of(
                            "type", "OBJECT",
                            "properties", new LinkedHashMap<>(Map.of(
                                    "name", Map.of("type", "STRING"),
                                    "type", Map.of("type", "STRING",
                                            "enum", List.of("SPOT", "MEAL", "STAY", "MOVE", "SHOPPING", "ETC")),
                                    "reason", Map.of("type", "STRING"))),
                            "required", List.of("name", "type", "reason")))),
            "required", List.of("suggestions"));

    public List<DaySuggestion> suggest(Long tripId, Long dayId, Long userId) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        if (!gemini.isAvailable()) {
            throw new AiException("AI 기능이 비활성화되어 있습니다(GEMINI_API_KEY 미설정).");
        }
        PlanDay day = planDayRepository.findById(dayId)
                .orElseThrow(() -> new NotFoundException("일자를 찾을 수 없습니다: " + dayId));
        Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(tripId).orElse(null);

        String json = gemini.generateJson(buildPrompt(trip, day, plan), SCHEMA);
        try {
            JsonNode root = objectMapper.readTree(json);
            List<DaySuggestion> out = new ArrayList<>();
            for (JsonNode s : root.path("suggestions")) {
                String name = s.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                out.add(new DaySuggestion(name.trim(), parseType(s.path("type").asText("SPOT")),
                        s.path("reason").asText("")));
            }
            return out;
        } catch (Exception e) {
            throw new AiException("AI 추천 응답 처리 실패: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(Trip trip, PlanDay day, Plan plan) {
        String dest = trip.getDestinationName() != null ? trip.getDestinationName() : trip.getTitle();

        // 이미 담긴 장소(전 일자) — 중복 추천 방지
        Set<String> already = new LinkedHashSet<>();
        if (plan != null) {
            for (PlanDay d : plan.getDays()) {
                for (PlanItem it : d.getItems()) {
                    already.add(it.getTitle());
                    if (it.getPlace() != null && it.getPlace().getName() != null) {
                        already.add(it.getPlace().getName());
                    }
                }
            }
        }
        String concept = trip.getConcept() != null && !trip.getConcept().isBlank()
                ? "- 여행 컨셉: " + trip.getConcept() + "\n" : "";

        return """
                너는 여행 일정 도우미야. 아래 조건으로 '%s일차(%s)'에 다녀오면 좋을 장소를 4곳 추천해라.
                - 목적지: %s
                %s- 하루 동선으로 무리 없이 묶이는 실제 존재하는 장소만.
                - 관광 위주로 하되 식사(MEAL) 한 곳은 포함.
                - name 은 지도에서 검색될 정확한 장소명(상호/명소명). 지역명만 쓰지 말 것.
                - 아래 '이미 담은 장소'와 겹치지 않게: %s
                - reason 은 한국어로 한 문장(왜 좋은지).
                지정된 JSON 스키마로만 응답해.
                """.formatted(
                day.getDayNo(), day.getDate(), dest, concept,
                already.isEmpty() ? "(없음)" : String.join(", ", already));
    }

    private static PlanItemType parseType(String s) {
        try {
            return PlanItemType.valueOf(s);
        } catch (Exception e) {
            return PlanItemType.SPOT;
        }
    }
}
