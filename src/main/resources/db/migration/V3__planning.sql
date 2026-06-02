-- =====================================================================
-- V3: Planning - 일정 트리 (설계 2.2)
-- Trip 1:N Plan(버전) 1:N PlanDay 1:N PlanItem ──? Place
-- 장소는 1차에서 수동입력(provider=MANUAL). 추후 지도 API 연동 시 GOOGLE/KAKAO.
-- =====================================================================

CREATE TABLE places (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    provider          VARCHAR(20)   NOT NULL DEFAULT 'MANUAL', -- MANUAL / GOOGLE / KAKAO
    provider_place_id VARCHAR(100)  NULL,                      -- 외부 API place id (수동입력 시 NULL)
    name              VARCHAR(150)  NOT NULL,
    category          VARCHAR(50)   NULL,
    latitude          DECIMAL(10,7) NULL,                      -- 좌표는 DECIMAL (설계 4-A)
    longitude         DECIMAL(10,7) NULL,
    address           VARCHAR(255)  NULL,

    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    deleted           TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at        DATETIME(6)   NULL,

    PRIMARY KEY (id),
    -- 외부 API 중복 캐시 방지. MANUAL은 provider_place_id=NULL → 다중 NULL 허용되어 중복 입력 가능.
    UNIQUE KEY uk_places_provider (provider, provider_place_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE plans (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    trip_id         BIGINT      NOT NULL,
    version         INT         NOT NULL,                  -- 재추천 시 증가
    generated_by    VARCHAR(10) NOT NULL DEFAULT 'USER',   -- AI / USER
    ai_model        VARCHAR(50) NULL,
    prompt_snapshot JSON        NULL,                      -- 생성 당시 입력조건 스냅샷

    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted         TINYINT(1)  NOT NULL DEFAULT 0,
    deleted_at      DATETIME(6) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_plans_trip_version (trip_id, version),
    CONSTRAINT fk_plans_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE plan_days (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    plan_id    BIGINT      NOT NULL,
    day_no     INT         NOT NULL,    -- 1일차, 2일차...
    plan_date  DATE        NOT NULL,

    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted    TINYINT(1)  NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_days (plan_id, day_no),
    CONSTRAINT fk_plan_days_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE plan_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    plan_day_id   BIGINT        NOT NULL,
    place_id      BIGINT        NULL,
    type          VARCHAR(20)   NOT NULL,            -- SPOT / MEAL / MOVE / STAY / ACTIVITY
    title         VARCHAR(100)  NOT NULL,
    planned_start TIME          NULL,
    planned_end   TIME          NULL,
    est_cost      DECIMAL(10,2) NULL,                -- 예상 비용 (DECIMAL, 설계 5.2)
    sort_order    INT           NOT NULL DEFAULT 0,

    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted       TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at    DATETIME(6)   NULL,

    PRIMARY KEY (id),
    KEY idx_plan_items_day (plan_day_id),
    CONSTRAINT fk_plan_items_day FOREIGN KEY (plan_day_id) REFERENCES plan_days (id),
    CONSTRAINT fk_plan_items_place FOREIGN KEY (place_id) REFERENCES places (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
