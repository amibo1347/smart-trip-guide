package com.travel.planner.common.util;

/** 문자열 공통 헬퍼(이전엔 여러 서비스에 blankToNull 가 중복 정의됨). */
public final class Texts {

    private Texts() {
    }

    /** null 또는 공백뿐이면 null, 아니면 그대로. */
    public static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** null 또는 공백뿐이면 null, 아니면 trim 한 값. */
    public static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
