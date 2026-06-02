-- =====================================================================
-- V5: 숙박 (설계 2.2 Accommodation). 여행별 숙박 예약. 장소는 선택적 연결.
-- =====================================================================

CREATE TABLE accommodations (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id    BIGINT        NOT NULL,
    place_id   BIGINT        NULL,
    name       VARCHAR(150)  NOT NULL,
    check_in   DATE          NOT NULL,
    check_out  DATE          NOT NULL,
    cost       DECIMAL(10,2) NULL,

    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    deleted    TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at DATETIME(6)   NULL,

    PRIMARY KEY (id),
    KEY idx_acc_trip (trip_id),
    CONSTRAINT fk_acc_trip  FOREIGN KEY (trip_id)  REFERENCES trips (id),
    CONSTRAINT fk_acc_place FOREIGN KEY (place_id) REFERENCES places (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
