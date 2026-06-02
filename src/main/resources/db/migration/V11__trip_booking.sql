-- =====================================================================
-- V11: 확정 예약 (사용자가 외부 사이트에서 고른 항공/숙소를 링크로 가져와 저장).
-- AI 계획(tentative)과 구분되는 '실제 선택' 기록. 가격은 사용자 확인값(환각 없음).
-- =====================================================================

CREATE TABLE trip_bookings (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id     BIGINT        NOT NULL,
    type        VARCHAR(10)   NOT NULL,            -- FLIGHT / HOTEL
    title       VARCHAR(200)  NOT NULL,
    price       DECIMAL(12,2) NULL,                -- 사용자가 사이트에서 본 가격
    booking_url VARCHAR(1000) NULL,                -- 예약 페이지 링크
    image_url   VARCHAR(1000) NULL,                -- 대표 이미지(og:image)
    start_date  DATE          NULL,                -- 숙박 체크인 / 항공 출발일
    end_date    DATE          NULL,                -- 숙박 체크아웃
    memo        VARCHAR(300)  NULL,

    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    deleted     TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at  DATETIME(6)   NULL,

    PRIMARY KEY (id),
    KEY idx_booking_trip (trip_id),
    CONSTRAINT fk_booking_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
