-- =====================================================================
-- V13: 기록(trip_moments)에 지역명(place) 추가
--  - 좌표 대신 사람이 읽는 행정구역명(예: "경기도 하남시 미사1동")을 저장.
--  - 저장 시점에 좌표를 역지오코딩(OpenStreetMap Nominatim)해 채움. 실패/위치없음 시 NULL.
-- =====================================================================

ALTER TABLE trip_moments ADD COLUMN place VARCHAR(120) NULL AFTER accuracy_m;
