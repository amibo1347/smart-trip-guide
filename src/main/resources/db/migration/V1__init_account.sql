-- =====================================================================
-- V1: Account 영역 베이스라인 (설계 2.1)
-- 모든 테이블 공통: BaseEntity 컬럼(created_at, updated_at, deleted, deleted_at)
-- 금액은 DECIMAL, 시각은 DATETIME(6) (설계 5.2 / 5.3)
-- =====================================================================

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,

    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at    DATETIME(6)  NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
