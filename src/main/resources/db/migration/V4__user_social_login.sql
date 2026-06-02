-- =====================================================================
-- V4: 소셜 로그인 지원 (Google / Kakao)
-- users 에 인증 제공자 정보 추가. 소셜 사용자는 password_hash 가 없으므로 NULL 허용.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' AFTER nickname,  -- LOCAL / GOOGLE / KAKAO
    ADD COLUMN provider_id VARCHAR(100) NULL                     AFTER provider,  -- 소셜 제공자의 고유 id
    MODIFY COLUMN password_hash VARCHAR(255) NULL;

-- 동일 소셜 제공자 내 중복 가입 방지. LOCAL 은 provider_id=NULL → 다중 NULL 허용.
ALTER TABLE users
    ADD UNIQUE KEY uk_users_provider (provider, provider_id);
