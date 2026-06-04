-- =====================================================================
-- V14: 확정 예약 → 일정 항목 연동.
-- 예약(항공/숙소)을 저장하면 해당 날짜에 일정 항목(STAY/MOVE)을 자동 생성하고,
-- 그 항목이 어느 예약에서 왔는지 booking_id 로 연결한다(예약 삭제 시 함께 정리).
-- FK 제약은 두지 않는다(예약/일정 삭제 순서에 결합되지 않도록) — 정리는 애플리케이션이 담당.
-- =====================================================================

ALTER TABLE plan_items
    ADD COLUMN booking_id BIGINT NULL AFTER place_id;

CREATE INDEX idx_plan_item_booking ON plan_items (booking_id);
