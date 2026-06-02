package com.travel.planner.common.exception;

/**
 * AI(외부 LLM) 호출 불가/실패 (HTTP 503). 예: 키 미설정, Google 일시 장애.
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
