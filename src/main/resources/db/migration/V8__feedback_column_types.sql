-- =====================================================================
-- V8: V7 컬럼 타입 정정 — JPA(Integer/String) 검증과 일치시키기.
--   satisfaction/overall_score: TINYINT → INT (Integer 매핑)
--   comment: TEXT → VARCHAR(2000) (String 매핑)
-- =====================================================================

ALTER TABLE plan_item_actuals MODIFY COLUMN satisfaction INT NULL;

ALTER TABLE trip_feedbacks MODIFY COLUMN overall_score INT NULL;
ALTER TABLE trip_feedbacks MODIFY COLUMN comment VARCHAR(2000) NULL;
