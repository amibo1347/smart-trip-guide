-- =====================================================================
-- V12: 기록 통합(trip_moments) + 여행 물리삭제용 FK ON DELETE CASCADE
--  - 위치/기분/지출 3테이블 → 단일 trip_moments 로 통합(언제·어디서·기분·금액·메모·사진).
--  - 여행 삭제 시 하위 데이터가 함께 지워지도록 trip 참조 FK를 CASCADE 로 재정의.
-- =====================================================================

-- 1) 기존 기록 테이블 제거(완전 대체). mood_logs 가 location_logs 를 참조하므로 먼저 삭제.
DROP TABLE IF EXISTS mood_logs;
DROP TABLE IF EXISTS location_logs;
DROP TABLE IF EXISTS expenses;

-- 2) 통합 기록 테이블. 위치·기분·금액·메모·사진 모두 선택(nullable).
CREATE TABLE trip_moments (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id     BIGINT        NOT NULL,
    client_uuid VARCHAR(64)   NOT NULL,             -- 멱등키(오프라인 큐 재전송 중복 방지)
    recorded_at DATETIME(6)   NOT NULL,             -- 기록 시각(클라이언트 wall-clock)
    latitude    DECIMAL(10,7) NULL,                 -- 위치(선택)
    longitude   DECIMAL(10,7) NULL,
    accuracy_m  FLOAT         NULL,
    mood        VARCHAR(16)   NULL,                 -- 기분 이모지(선택)
    amount      DECIMAL(10,2) NULL,                 -- 지출(선택)
    category    VARCHAR(30)   NULL,                 -- 지출 분류(선택)
    memo        VARCHAR(200)  NULL,                 -- 한 줄 메모(선택)
    photo_url   VARCHAR(500)  NULL,                 -- 그 순간 사진 URL(선택)

    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    deleted     TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at  DATETIME(6)   NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_moment_idem (trip_id, client_uuid),
    KEY idx_moment_trip (trip_id),
    CONSTRAINT fk_moment_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 3) 여행 삭제 시 연쇄 삭제되도록 기존 FK를 CASCADE 로 재정의.
--    trips → plans → plan_days → plan_items → plan_item_actuals 체인 + trips 직접 참조들.
ALTER TABLE plan_item_actuals DROP FOREIGN KEY fk_pia_item;
ALTER TABLE plan_item_actuals ADD CONSTRAINT fk_pia_item
    FOREIGN KEY (plan_item_id) REFERENCES plan_items (id) ON DELETE CASCADE;

ALTER TABLE plan_items DROP FOREIGN KEY fk_plan_items_day;
ALTER TABLE plan_items ADD CONSTRAINT fk_plan_items_day
    FOREIGN KEY (plan_day_id) REFERENCES plan_days (id) ON DELETE CASCADE;

ALTER TABLE plan_days DROP FOREIGN KEY fk_plan_days_plan;
ALTER TABLE plan_days ADD CONSTRAINT fk_plan_days_plan
    FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE;

ALTER TABLE plans DROP FOREIGN KEY fk_plans_trip;
ALTER TABLE plans ADD CONSTRAINT fk_plans_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

ALTER TABLE accommodations DROP FOREIGN KEY fk_acc_trip;
ALTER TABLE accommodations ADD CONSTRAINT fk_acc_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

ALTER TABLE trip_feedbacks DROP FOREIGN KEY fk_tf_trip;
ALTER TABLE trip_feedbacks ADD CONSTRAINT fk_tf_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

ALTER TABLE trip_bookings DROP FOREIGN KEY fk_booking_trip;
ALTER TABLE trip_bookings ADD CONSTRAINT fk_booking_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;
