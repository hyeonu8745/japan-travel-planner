-- V6: 관광지 Google 장소 확인 기록 (11-16). 장소 ID·평점 받기를 여러 번 나눠 돌려도 이미 확인한 곳은 건너뛰기 위해서
-- google_match_status: MATCHED(같은 곳 찾음) / NOT_FOUND(못 찾음) / CLOSED(폐업). google_checked_at: 확인한 시각
ALTER TABLE public.attractions
    ADD COLUMN google_match_status character varying(20),
    ADD COLUMN google_checked_at   timestamp(6) without time zone;
