-- 확장 5단계 (11-22): 숙소에서 당일치기로 다녀올 도시 (예: 'KYOTO,NARA'). 비어 있으면 당일치기 없음
ALTER TABLE public.trips ADD COLUMN day_trip_cities VARCHAR(200);
