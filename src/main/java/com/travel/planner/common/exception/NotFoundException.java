package com.travel.planner.common.exception;

/**
 * 리소스를 찾지 못했을 때 (HTTP 404).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
