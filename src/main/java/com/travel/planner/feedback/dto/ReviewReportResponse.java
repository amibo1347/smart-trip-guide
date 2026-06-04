package com.travel.planner.feedback.dto;

import java.util.List;

/**
 * AI 여행 회고 리포트(설계 2.4 확장): DB에 기록된 일정·지출·기분·만족도·회고를 근거로
 * Gemini가 생성한 요약. 모든 수치는 DB 집계값을 프롬프트로 넣어 환각을 최소화한다.
 */
public record ReviewReportResponse(
        String title,             // 한 줄 총평
        List<String> highlights,  // 인상적인 순간/하이라이트
        String spending,          // 지출 분석 한 문단
        String mood,              // 기분·만족도 한 문단
        List<String> tips         // 다음 여행을 위한 팁
) {
}
