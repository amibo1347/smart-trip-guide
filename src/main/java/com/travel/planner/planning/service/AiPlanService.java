package com.travel.planner.planning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.planner.common.exception.AiException;
import com.travel.planner.planning.ai.GeminiClient;
import com.travel.planner.planning.dto.GeneratePlanRequest;
import com.travel.planner.planning.dto.PlanResponse;
import com.travel.planner.planning.entity.GeneratedBy;
import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.PlaceProvider;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.entity.PlanItemType;
import com.travel.planner.booking.entity.BookingType;
import com.travel.planner.booking.entity.TripBooking;
import com.travel.planner.booking.repository.TripBookingRepository;
import com.travel.planner.planning.repository.PlaceRepository;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final TripBookingRepository bookingRepository;
    private final PlanService planService;
    private final ObjectMapper objectMapper;

    private static final Map<String, Object> ITEM_SCHEMA = Map.of(
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
            "required", List.of("type", "title"));

    /** 비용 추정 응답 스키마: { estimates:[{ id, estCost }] } */
    private static final Map<String, Object> COST_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "estimates", Map.of("type", "ARRAY", "items", Map.of(
                            "type", "OBJECT",
                            "properties", new LinkedHashMap<>(Map.of(
                                    "id", Map.of("type", "INTEGER"),
                                    "estCost", Map.of("type", "NUMBER"))),
                            "required", List.of("id", "estCost")))),
            "required", List.of("estimates"));

    /** Gemini 응답 스키마: { destinationCity, days:[{ dayNo, items:[...] }] } */
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", new LinkedHashMap<>(Map.of(
                    "destinationCity", Map.of("type", "STRING"),
                    "days", Map.of("type", "ARRAY", "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "dayNo", Map.of("type", "INTEGER"),
                                    "items", Map.of("type", "ARRAY", "items", ITEM_SCHEMA)),
                            "required", List.of("dayNo", "items"))))),
            "required", List.of("destinationCity", "days"));

    @Transactional
    public PlanResponse generate(Long tripId, Long userId, GeneratePlanRequest req) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);
        long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;

        // 확정 예약(항공/숙소) — AI가 이를 전제로 일정을 짜도록 프롬프트에 반영
        List<TripBooking> bookings = bookingRepository.findByTripIdOrderByTypeAscIdAsc(tripId);

        String prompt = buildPrompt(trip, days, req, bookings);
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
                .destinationCity(text(root, "destinationCity"))
                .promptSnapshot(snapshot(trip, req))
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
        JsonNode daysNode = root.path("days");
        if (daysNode.isArray()) {
            for (JsonNode dayNode : daysNode) {
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

        Plan saved = planRepository.save(plan);
        fillMissingCosts(trip, saved); // 생성 직후, 비용이 비어 있는 항목은 AI 추정으로 자동 보강

        // 확정 예약을 새 플랜에도 항목으로 반영(호텔 체크인/항공 출발 등). AI 항목과 중복되지 않도록
        // 프롬프트에서 AI가 항공/숙소 항목을 따로 만들지 않게 지시했다.
        for (TripBooking b : bookings) {
            planService.addBookingItems(tripId, userId, b.getId(),
                    b.getType() == BookingType.HOTEL, b.getTitle(), b.getStartDate(), b.getEndDate());
        }
        return PlanResponse.from(saved);
    }

    /**
     * 일정의 식사/명소/액티비티 항목 중 '비용이 비어 있는(null·0)' 것들의 1인 예상비용을
     * AI로 한 번에 추정해 채운다(직접 입력값 보존). 생성 흐름의 보조 단계 —
     * 실패해도 일정 생성 자체는 유지되도록 예외를 삼킨다.
     */
    private void fillMissingCosts(Trip trip, Plan plan) {
        List<PlanItem> targets = new ArrayList<>();
        for (PlanDay day : plan.getDays()) {
            for (PlanItem it : day.getItems()) {
                boolean costly = it.getType() == PlanItemType.MEAL
                        || it.getType() == PlanItemType.SPOT
                        || it.getType() == PlanItemType.ACTIVITY;
                boolean empty = it.getEstCost() == null || it.getEstCost().signum() == 0;
                if (costly && empty) {
                    targets.add(it);
                }
            }
        }
        if (targets.isEmpty()) {
            return; // 생성 프롬프트가 이미 다 채웠으면 추가 호출 없음
        }

        JsonNode root;
        try {
            String json = gemini.generateJson(buildCostPrompt(trip, plan, targets), COST_SCHEMA);
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            return; // 비용 보강 실패는 무시(일정은 그대로 유지)
        }

        Map<Long, PlanItem> byId = new HashMap<>();
        for (PlanItem it : targets) {
            byId.put(it.getId(), it);
        }
        JsonNode estimates = root.path("estimates");
        if (estimates.isArray()) {
            for (JsonNode e : estimates) {
                PlanItem it = byId.get(e.path("id").asLong(0));
                if (it != null && e.hasNonNull("estCost") && e.get("estCost").isNumber()) {
                    BigDecimal cost = e.get("estCost").decimalValue();
                    if (cost.signum() > 0) {
                        it.changeEstCost(cost);
                    }
                }
            }
        }
    }

    private String buildCostPrompt(Trip trip, Plan plan, List<PlanItem> targets) {
        String dest = plan.getDestinationCity() != null ? plan.getDestinationCity() : trip.getTitle();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlanItem it : targets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", it.getId());
            row.put("type", it.getType().name());
            row.put("title", it.getTitle());
            row.put("place", it.getPlace() == null ? null : it.getPlace().getName());
            rows.add(row);
        }
        String itemsJson;
        try {
            itemsJson = objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            itemsJson = "[]";
        }
        return """
                너는 여행 식비/활동비 추정 도우미야. 아래 항목들의 '1인 평균 객단가'를 원화 정수로 추정해라.
                - 목적지: %s
                - 각 항목 title/place 에 적힌 구체적 상호/장소 기준, 1명이 실제 지불할 평균 금액(대표 메뉴 또는 입장료).
                - 현지 통화가 아니라 반드시 '원화'로 환산. 정말 모르겠으면 0.
                - 최신 일반 시세 기준의 추정치. 애매하면 과소·과대 없이 보수적으로.
                - 입력의 모든 id 에 대해 각각 estCost 를 돌려줘라.
                항목 목록(JSON): %s
                지정된 JSON 스키마로만 응답해.
                """.formatted(dest, itemsJson);
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

    /** 확정 예약을 프롬프트용 텍스트로. 없으면 "없음". */
    private String bookingsText(List<TripBooking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return "없음";
        }
        StringBuilder sb = new StringBuilder();
        for (TripBooking b : bookings) {
            boolean hotel = b.getType() == BookingType.HOTEL;
            sb.append(hotel ? "  - [숙소] " : "  - [항공] ").append(b.getTitle());
            if (hotel) {
                sb.append(" (").append(b.getStartDate()).append(" 체크인 ~ ").append(b.getEndDate()).append(" 체크아웃)");
            } else {
                sb.append(" (").append(b.getStartDate()).append(" 출발");
                if (b.getEndDate() != null && !b.getEndDate().equals(b.getStartDate())) {
                    sb.append(" ~ ").append(b.getEndDate()).append(" 귀국");
                }
                sb.append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildPrompt(Trip trip, long days, GeneratePlanRequest req, List<TripBooking> bookings) {
        String origin = (req.origin() == null || req.origin().isBlank()) ? "서울" : req.origin();
        String flightPref = switch (req.flightTime() == null ? "" : req.flightTime().toUpperCase()) {
            case "MORNING" -> "오전 출발 선호";
            case "AFTERNOON" -> "낮 출발 선호";
            case "EVENING" -> "저녁 출발 선호";
            default -> "시간대 무관";
        };
        String lowCost = Boolean.TRUE.equals(req.lowCost()) ? "저가항공(LCC) 선호" : "항공사 무관";
        String note = (req.note() == null || req.note().isBlank()) ? "없음" : req.note();

        return """
                너는 한국인 대상 여행 일정 플래너야. 아래 조건으로 현실적이고 '구체적인' 일정을 짜줘.
                - 출발지: %s
                - 제목/목적지: %s
                - 기간: %s ~ %s (총 %d일)
                - 인원: %d명
                - 전체 예산 한도: %s (이 안에 항공+숙박+현지비용이 모두 들어가야 함)
                - 컨셉: %s
                - 항공 선호: %s, %s
                - 추가 요청: %s
                - 이미 확정된 예약(이 날짜·항공/숙소를 반드시 전제로 삼아 일정을 구성):
                %s

                매우 중요한 규칙:
                - destinationCity 에는 목적지 '도시명'만 한국어로 (예: "오사카", "부산").
                - MEAL(식사)/SPOT(명소)/ACTIVITY 는 '실제 존재하는 구체적 상호명'을 title 에 넣어라.
                  나쁜 예: "점심 식사" / 좋은 예: "이치란 라멘 도톤보리점에서 점심".
                  placeName 에는 그 장소의 정확한 상호명, address 에는 대략 위치(동/구/거리)를 넣어라.
                - 항공권과 숙소는 '실시간 예약'이 필요하므로 일정 항목으로 비용을 지어내지 마라(estCost 0).
                - '확정된 예약'에 이미 있는 항공/숙소는 시스템이 일정에 자동으로 넣는다. 너는 그 항공편/숙소에 대한
                  STAY/MOVE 항목을 새로 만들지 마라. 대신 그 예약을 전제로(항공 출발·귀국 날짜, 숙소 체크인·아웃/위치)
                  나머지 동선을 현실적으로 짜라(도착 첫날은 숙소 체크인 이후 근처부터, 마지막날은 출발 시간 전까지).
                - 확정 예약이 '없는' 경우에만, 공항↔도심 이동은 MOVE(estCost 0), 숙소 체크인/아웃은 STAY(estCost 0)로 넣어도 된다.
                - MEAL/SPOT/ACTIVITY 의 estCost 는 그 '구체적 상호'에서 1명이 실제로 지불할 평균 객단가를
                  '원화 정수'로 추정해라(대표 메뉴/입장료 기준). 예: 이치란 라멘 1인 ≈ 12000, 성산일출봉 입장 ≈ 5000.
                  - 현지 통화가 아니라 반드시 원화로 환산해 넣어라. 정말 모르겠으면 0.
                  - 최신 일반 시세 기준의 '추정치'이며, 애매하면 과소·과대 없이 보수적으로 잡아라.
                  - 항공+숙박을 제외한 현지 비용(estCost) 합이 전체 예산을 넘지 않도록 조절해라.
                - dayNo 는 1부터 %d 까지. 각 일자 4~6개 항목, plannedStart/End 는 "HH:mm".
                지정된 JSON 스키마로만 응답해.
                """.formatted(
                origin, trip.getTitle(),
                trip.getStartDate(), trip.getEndDate(), days,
                trip.getHeadcount(),
                trip.getBudgetLimit() == null ? "미설정" : trip.getBudgetLimit().toPlainString() + "원",
                trip.getConcept() == null ? "특별히 없음" : trip.getConcept(),
                flightPref, lowCost, note,
                bookingsText(bookings),
                days);
    }

    private String snapshot(Trip trip, GeneratePlanRequest req) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("title", trip.getTitle());
            snap.put("startDate", trip.getStartDate().toString());
            snap.put("endDate", trip.getEndDate().toString());
            snap.put("headcount", trip.getHeadcount());
            snap.put("budgetLimit", trip.getBudgetLimit());
            snap.put("concept", trip.getConcept());
            snap.put("origin", req.origin());
            snap.put("flightTime", req.flightTime());
            snap.put("lowCost", req.lowCost());
            snap.put("note", req.note());
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
