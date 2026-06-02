package com.travel.planner.trip.entity;

/**
 * 여행 진행 상태 (설계 2.2 Trip.status).
 */
public enum TripStatus {
    PLANNED,   // 계획 단계(여행 전)
    ONGOING,   // 여행 중
    DONE       // 종료(여행 후/복기)
}
