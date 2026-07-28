-- =====================================================================
-- V18: 일정 기반 노선도 + 위치별 사진.
--  - 기존 'GPS 순간기록' 대신, 일정 항목(장소)을 지오코딩해 지도에 노선으로 잇고
--    각 장소에 사진을 첨부한다.
--  - places.geocode_attempted: 좌표가 없고 아직 지오코딩을 시도하지 않은 장소만 변환하기 위한 플래그
--    (변환 실패한 장소를 매번 다시 시도해 Nominatim 을 두드리는 것을 방지).
-- =====================================================================

ALTER TABLE places
    ADD COLUMN geocode_attempted TINYINT(1) NOT NULL DEFAULT 0 AFTER longitude;

-- 이미 좌표가 있는 장소는 '시도 완료'로 표시(불필요한 재지오코딩 방지).
UPDATE places SET geocode_attempted = 1 WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- 일정 항목별 사진. 한 장소(일정 항목)에 여러 장 첨부 가능.
-- plan_items 삭제(항목 제거/일정 재생성) 시 함께 지워지도록 FK CASCADE.
CREATE TABLE plan_item_photos (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    plan_item_id  BIGINT       NOT NULL,
    photo_url     VARCHAR(500) NOT NULL,           -- /uploads/{name}
    caption       VARCHAR(200) NULL,               -- 한 줄 설명(선택)
    sort_order    INT          NOT NULL DEFAULT 0,

    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at    DATETIME(6)  NULL,

    PRIMARY KEY (id),
    KEY idx_pip_item (plan_item_id),
    CONSTRAINT fk_pip_item FOREIGN KEY (plan_item_id) REFERENCES plan_items (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
