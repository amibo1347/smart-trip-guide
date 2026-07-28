-- 가계부(지출 내역) — 여행 지갑에서 직접 입력. N빵 정산을 위한 split_count/payer 포함.
CREATE TABLE expenses (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    trip_id      BIGINT       NOT NULL,
    spent_on     DATE         NULL,
    category     VARCHAR(20)  NULL,
    title        VARCHAR(120) NULL,
    amount       DECIMAL(12,2) NOT NULL,
    split_count  INT          NOT NULL DEFAULT 1,
    payer        VARCHAR(30)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_expenses_trip (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
