-- 여행 일기를 더 세세하게: 좋았던 점 / 아쉬웠던 점 / 다음엔 이렇게 섹션 추가.
ALTER TABLE trip_feedbacks
    ADD COLUMN liked     VARCHAR(1000) NULL AFTER comment,
    ADD COLUMN regret    VARCHAR(1000) NULL AFTER liked,
    ADD COLUMN next_time VARCHAR(1000) NULL AFTER regret;
