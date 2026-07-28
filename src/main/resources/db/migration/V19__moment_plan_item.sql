-- =====================================================================
-- V19: 기록(moment)을 '노선도의 장소(일정 항목)'에 연결.
--  - 원래 UX(한 위치에 사진+지출+메모를 함께 기록)를 되살리되, 위치 기준을 GPS 가 아니라
--    '일정 항목(plan_item)'으로 바꾼다. 사진 전용 테이블(V18 plan_item_photos)은 이 모델로 흡수 → 제거.
--  - 일정 항목 삭제 시 기록은 지우지 않고 연결만 끊는다(지출 이력·예산 보존) → ON DELETE SET NULL.
-- =====================================================================

-- 사진 전용 테이블 제거(도입 직후 미사용, 기록(moment)으로 통합).
DROP TABLE IF EXISTS plan_item_photos;

-- 기록을 일정 항목에 연결(선택). NULL 이면 특정 장소에 매이지 않은 일반 기록.
ALTER TABLE trip_moments
    ADD COLUMN plan_item_id BIGINT NULL AFTER trip_id;

ALTER TABLE trip_moments
    ADD KEY idx_moment_plan_item (plan_item_id),
    ADD CONSTRAINT fk_moment_plan_item FOREIGN KEY (plan_item_id)
        REFERENCES plan_items (id) ON DELETE SET NULL;
