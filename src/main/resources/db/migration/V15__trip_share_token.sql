-- =====================================================================
-- V15: 여행 일정 '읽기 전용 공유'. 여행마다 공개 토큰을 발급하면
-- 로그인 없이 /share/{token} 으로 일정을 볼 수 있다(카톡/링크 공유·PDF용).
-- 토큰을 비우면(폐기) 공유가 즉시 중단된다.
-- =====================================================================

ALTER TABLE trips
    ADD COLUMN share_token VARCHAR(64) NULL AFTER status;

CREATE UNIQUE INDEX uq_trip_share_token ON trips (share_token);
