package com.travel.planner.planning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.planner.common.exception.AiException;
import com.travel.planner.planning.ai.GeminiClient;
import com.travel.planner.planning.dto.PlanResponse;
import com.travel.planner.planning.entity.GeneratedBy;
import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.PlaceProvider;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.entity.PlanItemType;
import com.travel.planner.planning.repository.PlaceRepository;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI(Gemini) 일정 생성 (설계 4.C). 여행 조건으로 프롬프트를 만들고, 구조화 JSON 응답을
 * 새 Plan 버전(generatedBy=AI)으로 저장한다. 일자/시간/숫자는 서버에서 재검증.
 */
@Service
@RequiredArgsConstructor
public class AiPlanService {

    private final GeminiClient gemini;
    private final TripService tripService;
    private final PlanRepository planRepository;
    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;

    /** Gemini 응답 스키마: [{ dayNo, items:[{type,title,plannedStart,plannedEnd,estCost,placeName,address}] }] */
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "dayNo", Map.of("type", "INTEGER"),
                            "items", Map.of("type", "ARRAY", "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", new LinkedHashMap<>(Map.of(
                                            "type", Map.of("type", "STRING", "enum",
                                                    List.of("SPOT", "MEAL", "MOVE", "STAY", "ACTIVITY")),
                                            "title", Map.of("type", "STRING"),
                                            "plannedStart", Map.of("type", "STRING"),
                                            "plannedEnd", Map.of("type", "STRING"),
                                            "estCost", Map.of("type", "NUMBER"),
                                            "placeName", Map.of("type", "STRING"),
                                            "address", Map.of("type", "STRING"))),
                                    "required", List.of("type", "title")))),
                    "required", List.of("dayNo", "items")));

    @Transactional
    public PlanResponse generate(Long tripId, Long userId, String note) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;

        String prompt = buildPrompt(trip, days, note);
        String json = gemini.generateJson(prompt, SCHEMA);

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AiException("AI 응답(JSON) 파싱 실패: " + e.getMessage(), e);
        }

        int version = planRepository.findTopByTripIdOrderByVersionDesc(tripId)
                .map(p -> p.getVersion() + 1).orElse(1);

        Plan plan = Plan.builder()
                .tripId(tripId)
                .version(version)
                .generatedBy(GeneratedBy.AI)
                .aiModel(gemini.model())
                .promptSnapshot(snapshot(trip, note))
                .build();

        // 여행 기간만큼 일자 생성(서버가 신뢰 가능한 날짜로 고정)
        Map<Integer, PlanDay> dayByNo = new HashMap<>();
        int dayNo = 1;
        for (LocalDate d = trip.getStartDate(); !d.isAfter(trip.getEndDate()); d = d.plusDays(1)) {
            PlanDay day = PlanDay.builder().dayNo(dayNo).date(d).build();
            plan.addDay(day);
            dayByNo.put(dayNo, day);
            dayNo++;
        }

        // AI 항목을 해당 일자에 부착 (일자 범위를 벗어난 dayNo는 무시 — 서버 재검증)
        if (root.isArray()) {
            for (JsonNode dayNode : root) {
                int no = dayNode.path("dayNo").asInt(0);
                PlanDay day = dayByNo.get(no);
                if (day == null) {
                    continue;
                }
                JsonNode items = dayNode.path("items");
                int sort = 0;
                if (items.isArray()) {
                    for (JsonNode it : items) {
                        day.addItem(toItem(it, sort++));
                    }
                }
            }
        }

        return PlanResponse.from(planRepository.save(plan));
    }

    private PlanItem toItem(JsonNode it, int sort) {
        Place place = null;
        String placeName = text(it, "placeName");
        if (placeName != null) {
            place = placeRepository.save(Place.builder()
                    .provider(PlaceProvider.MANUAL)
                    .name(placeName)
                    .address(text(it, "address"))
                    .build());
        }
        return PlanItem.builder()
                .place(place)
                .type(parseType(text(it, "type")))
                .title(text(it, "title") == null ? "(제목 없음)" : text(it, "title"))
                .plannedStart(parseTime(text(it, "plannedStart")))
                .plannedEnd(parseTime(text(it, "plannedEnd")))
                .estCost(it.hasNonNull("estCost") && it.get("estCost").isNumber()
                        ? it.get("estCost").decimalValue() : null)
                .sortOrder(sort)
                .build();
    }

    private String buildPrompt(Trip trip, long days, String note) {
        return """
                너는 한국어 여행 일정 플래너야. 아래 조건으로 현실적인 여행 일정을 만들어줘.
                - 제목/목적지: %s
                - 기간: %s ~ %s (총 %d일)
                - 인원: %d명
                - 예산 한도: %s
                - 컨셉: %s
                - 추가 요청: %s

                규칙:
                - dayNo 는 1부터 %d 까지. 각 일자에 3~6개 항목.
                - type 은 SPOT(명소)/MEAL(식사)/MOVE(이동)/STAY(숙박)/ACTIVITY 중 하나.
                - plannedStart/plannedEnd 는 "HH:mm" 24시간 형식.
                - estCost 는 1인 기준 원화 정수(추정). 모르면 0.
                - placeName/address 는 실제 존재할 법한 장소/주소(한국어).
                - 예산 한도를 의식해서 합리적으로 배분.
                지정된 JSON 스키마로만 응답해.
                """.formatted(
                trip.getTitle(),
                trip.getStartDate(), trip.getEndDate(), days,
                trip.getHeadcount(),
                trip.getBudgetLimit() == null ? "미설정" : trip.getBudgetLimit().toPlainString() + "원",
                trip.getConcept() == null ? "특별히 없음" : trip.getConcept(),
                (note == null || note.isBlank()) ? "없음" : note,
                days);
    }

    private String snapshot(Trip trip, String note) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("title", trip.getTitle());
            snap.put("startDate", trip.getStartDate().toString());
            snap.put("endDate", trip.getEndDate().toString());
            snap.put("headcount", trip.getHeadcount());
            snap.put("budgetLimit", trip.getBudgetLimit());
            snap.put("concept", trip.getConcept());
            snap.put("note", note);
            snap.put("model", gemini.model());
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static PlanItemType parseType(String t) {
        if (t == null) {
            return PlanItemType.SPOT;
        }
        try {
            return PlanItemType.valueOf(t.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PlanItemType.SPOT;
        }
    }

    private static LocalTime parseTime(String t) {
        if (t == null) {
            return null;
        }
        try {
            return LocalTime.parse(t.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
