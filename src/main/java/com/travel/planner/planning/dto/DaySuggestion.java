package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.PlanItemType;

/**
 * '이 날 AI 추천' 결과 한 건. 일정을 통째로 만들지 않고, 그 날에 담을 만한 장소를 몇 개 제안만 한다.
 * (AI 를 '주기능→부기능'으로 내리고, 사용자가 직접 담는 흐름을 돕는 용도)
 *
 * @param name   장소명(사용자가 담을 때 검색어로도 쓰인다)
 * @param type   추천 유형(관광/식사/쇼핑/…)
 * @param reason 왜 추천하는지 한 줄
 */
public record DaySuggestion(String name, PlanItemType type, String reason) {
}
