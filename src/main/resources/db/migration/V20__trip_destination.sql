-- =====================================================================
-- V20: 여행에 '목적지'를 심는다.
--  앱 흐름이 [AI가 다 짜줌] → [목적지 고르고 → 장소를 직접 담아 일정 완성]으로 바뀌면서,
--  인기 장소/주변 장소 탐색의 '중심 좌표'가 필요하다. 그 기준점을 여행 단위로 저장한다.
--  모두 nullable — 목적지 없이 만든 기존 여행/제목만 있는 여행도 그대로 동작한다.
-- =====================================================================

ALTER TABLE trips
    ADD COLUMN destination_name VARCHAR(120)  NULL AFTER title,
    ADD COLUMN destination_lat  DECIMAL(10,7) NULL AFTER destination_name,
    ADD COLUMN destination_lng  DECIMAL(10,7) NULL AFTER destination_lat;
