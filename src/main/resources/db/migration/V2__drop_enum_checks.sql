-- V2: Hibernate 가 enum 컬럼마다 만든 허용 값 CHECK 제약을 지운다 (11-14)
-- enum 에 값을 하나 더할 때마다 DB 제약도 같이 고쳐야 해서, 안 고치면 저장이 실패했다
-- (10-06 되돌리기 409 사고, 11-14 권역·도시 컬럼에도 똑같이 생김). 허용 값은 코드(enum·컨버터)가 지킨다
-- IF EXISTS: V1 로 새로 만든 DB 와 원래 DB 가 둘 다 같은 상태가 된다

ALTER TABLE public.chat_messages DROP CONSTRAINT IF EXISTS chat_messages_role_check;
ALTER TABLE public.itineraries DROP CONSTRAINT IF EXISTS itineraries_source_check;
ALTER TABLE public.itinerary_day_dropped DROP CONSTRAINT IF EXISTS itinerary_day_dropped_reason_check;
ALTER TABLE public.itinerary_days DROP CONSTRAINT IF EXISTS itinerary_days_zone_check;
ALTER TABLE public.itinerary_stops DROP CONSTRAINT IF EXISTS itinerary_stops_stop_type_check;
ALTER TABLE public.itinerary_stops DROP CONSTRAINT IF EXISTS itinerary_stops_travel_mode_check;
ALTER TABLE public.restaurants DROP CONSTRAINT IF EXISTS restaurants_google_match_status_check;
ALTER TABLE public.trip_interests DROP CONSTRAINT IF EXISTS trip_interests_interest_check;
ALTER TABLE public.trip_poster_pages DROP CONSTRAINT IF EXISTS trip_poster_pages_kind_check;
ALTER TABLE public.trip_poster_pages DROP CONSTRAINT IF EXISTS trip_poster_pages_status_check;
ALTER TABLE public.trip_posters DROP CONSTRAINT IF EXISTS trip_posters_status_check;
ALTER TABLE public.trip_posters DROP CONSTRAINT IF EXISTS trip_posters_style_check;
ALTER TABLE public.trip_preference_constraints DROP CONSTRAINT IF EXISTS trip_preference_constraints_category_check;
ALTER TABLE public.trips DROP CONSTRAINT IF EXISTS trips_base_city_check;
ALTER TABLE public.trips DROP CONSTRAINT IF EXISTS trips_region_check;
ALTER TABLE public.trips DROP CONSTRAINT IF EXISTS trips_status_check;
ALTER TABLE public.trips DROP CONSTRAINT IF EXISTS trips_transport_mode_check;
ALTER TABLE public.trips DROP CONSTRAINT IF EXISTS trips_travel_pace_check;
