package com.travel.planner.common.exception;

import java.util.Map;

/**
 * 표준 에러 응답 바디.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null);
    }

    public static ErrorResponse of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, error, message, fieldErrors);
    }
}
