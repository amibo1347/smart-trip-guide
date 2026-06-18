-- =====================================================================
-- V16: 목적지 도시의 대표 좌표 보관.
-- 지도 링크를 '그 도시 위치 중심'으로 띄워 검색하기 위함(엉뚱한 동명 검색 결과 방지).
-- =====================================================================

ALTER TABLE plans ADD COLUMN destination_lat DECIMAL(10,7) NULL AFTER destination_city;
ALTER TABLE plans ADD COLUMN destination_lng DECIMAL(10,7) NULL AFTER destination_lat;
