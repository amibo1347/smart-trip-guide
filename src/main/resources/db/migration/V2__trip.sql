-- =====================================================================
-- V2: Planning - Trip (설계 2.2). Trip = 전 과정의 루트 애그리거트.
-- =====================================================================

CREATE TABLE trips (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    title        VARCHAR(100)  NOT NULL,
    start_date   DATE          NOT NULL,
    end_date     DATE          NOT NULL,
    headcount    INT           NOT NULL DEFAULT 1,
    budget_limit DECIMAL(12,2) NULL,                    -- 예산 한도(금액은 DECIMAL, 설계 5.2)
    concept      VARCHAR(50)   NULL,                    -- 여행 컨셉(휴양/액티비티/맛집...)
    status       VARCHAR(20)   NOT NULL DEFAULT 'PLANNED', -- PLANNED / ONGOING / DONE

    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    deleted      TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at   DATETIME(6)   NULL,

    PRIMARY KEY (id),
    KEY idx_trips_user (user_id),
    CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
