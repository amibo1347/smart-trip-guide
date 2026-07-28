-- 목적지 대표 사진 URL 이 프록시 파라미터로 길어질 수 있어 500 → 1000 으로 확장
ALTER TABLE trips MODIFY COLUMN destination_photo VARCHAR(1000) NULL;
