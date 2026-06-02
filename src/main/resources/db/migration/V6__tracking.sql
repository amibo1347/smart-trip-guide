-- =====================================================================
-- V6: Tracking - 여행 중 기록 (설계 2.3)
-- 핵심: client_uuid 멱등키로 오프라인 큐 재전송 시 중복 INSERT 방지 (설계 4.B)
--       시각은 클라이언트 기록시각(recorded_at/spent_at) + 서버 수신시각(created_at) 이원화 (설계 5.3)
-- 사진(Photo)은 오브젝트 스토리지 필요 → 다음 단계.
-- =====================================================================

CREATE TABLE location_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id     BIGINT        NOT NULL,
    client_uuid VARCHAR(64)   NOT NULL,             -- 멱등키 (클라이언트 생성)
    recorded_at DATETIME(6)   NOT NULL,             -- 클라이언트 기록 시각
    latitude    DECIMAL(10,7) NOT NULL,
    longitude   DECIMAL(10,7) NOT NULL,
    accuracy_m  FLOAT         NULL,                 -- GPS 정확도(노이즈 필터용)
    place_id    BIGINT        NULL,

    created_at  DATETIME(6)   NOT NULL,             -- 서버 수신 시각
    updated_at  DATETIME(6)   NOT NULL,
    deleted     TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at  DATETIME(6)   NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_loc_idem (trip_id, client_uuid),
    KEY idx_loc_trip (trip_id),
    CONSTRAINT fk_loc_trip  FOREIGN KEY (trip_id)  REFERENCES trips (id),
    CONSTRAINT fk_loc_place FOREIGN KEY (place_id) REFERENCES places (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE mood_logs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    trip_id         BIGINT      NOT NULL,
    client_uuid     VARCHAR(64) NOT NULL,
    location_log_id BIGINT      NULL,
    emoji           VARCHAR(16) NOT NULL,
    recorded_at     DATETIME(6) NOT NULL,

    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted         TINYINT(1)  NOT NULL DEFAULT 0,
    deleted_at      DATETIME(6) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_mood_idem (trip_id, client_uuid),
    KEY idx_mood_trip (trip_id),
    CONSTRAINT fk_mood_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_mood_loc  FOREIGN KEY (location_log_id) REFERENCES location_logs (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE expenses (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id     BIGINT        NOT NULL,
    client_uuid VARCHAR(64)   NOT NULL,
    amount      DECIMAL(10,2) NOT NULL,             -- 금액은 DECIMAL (설계 5.2)
    category    VARCHAR(30)   NULL,                 -- 식비/교통/숙박/기타
    memo        VARCHAR(100)  NULL,
    spent_at    DATETIME(6)   NOT NULL,

    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    deleted     TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at  DATETIME(6)   NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_exp_idem (trip_id, client_uuid),
    KEY idx_exp_trip (trip_id),
    CONSTRAINT fk_exp_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
