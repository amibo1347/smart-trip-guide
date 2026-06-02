package com.travel.planner.common.exception;

/**
 * 유니크 제약 위반 등 중복 리소스 (HTTP 409).
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
