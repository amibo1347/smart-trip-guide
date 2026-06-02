-- =====================================================================
-- V7: Feedback - 여행 후 복기 (설계 2.4, 차별화 포인트)
-- 계획 vs 실제 비교 + 여행 단위 회고.
-- =====================================================================

CREATE TABLE plan_item_actuals (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    plan_item_id BIGINT        NOT NULL,
    visited      TINYINT(1)    NOT NULL DEFAULT 0,   -- 실제 방문 여부
    actual_cost  DECIMAL(10,2) NULL,                 -- 실제 비용
    actual_start DATETIME(6)   NULL,                 -- 실제 시작(추정/입력)
    satisfaction TINYINT       NULL,                 -- 1~5 만족도

    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    deleted      TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at   DATETIME(6)   NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pia_item (plan_item_id),
    CONSTRAINT fk_pia_item FOREIGN KEY (plan_item_id) REFERENCES plan_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE trip_feedbacks (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id       BIGINT        NOT NULL,
    overall_score TINYINT       NULL,                -- 1~5
    budget_diff   DECIMAL(12,2) NULL,                -- 계획 예산 - 실제 지출
    comment       TEXT          NULL,

    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted       TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at    DATETIME(6)   NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_tf_trip (trip_id),
    CONSTRAINT fk_tf_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
