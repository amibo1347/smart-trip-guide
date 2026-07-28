-- =====================================================================
-- V17: 준비물 체크리스트. 여행별로 챙길 것을 적어두고 체크하며,
--      담당자(assignee)를 적어 여러 명이 나눠 준비할 수 있다.
--      공유 링크(/share/{token})에서도 읽기 전용으로 함께 보인다.
-- =====================================================================

CREATE TABLE trip_checklist_items (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    trip_id     BIGINT       NOT NULL,
    title       VARCHAR(120) NOT NULL,             -- 준비물 이름 (예: 여권)
    category    VARCHAR(20)  NOT NULL,             -- DOCUMENT/CLOTHES/ELECTRONICS/TOILETRIES/MEDICINE/ETC
    assignee    VARCHAR(50)  NULL,                 -- 담당자(자유 입력) — 여러 명이 나눠 챙길 때
    checked     TINYINT(1)   NOT NULL DEFAULT 0,   -- 챙김 여부
    sort_order  INT          NOT NULL DEFAULT 0,   -- 같은 분류 안에서의 표시 순서

    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at  DATETIME(6)  NULL,

    PRIMARY KEY (id),
    KEY idx_checklist_trip (trip_id),
    CONSTRAINT fk_checklist_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
