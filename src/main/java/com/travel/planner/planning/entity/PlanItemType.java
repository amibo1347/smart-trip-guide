package com.travel.planner.planning.entity;

/** 일정 항목 유형 (설계 2.2 PlanItem.type). */
public enum PlanItemType {
    SPOT,      // 관광·명소
    MEAL,      // 식사
    MOVE,      // 교통·이동
    STAY,      // 숙박
    ACTIVITY,  // 액티비티(기존 데이터 호환용)
    SHOPPING,  // 쇼핑
    ETC        // 기타
}
