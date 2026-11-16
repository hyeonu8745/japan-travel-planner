-- V5: 확장 데이터 정규화와 빠진 키 (11-16)
-- 1) 체인점 브랜드 정보(한국어 이름·종류·장르·예산)를 브랜드 표로 뺀다: 지점마다 같은 값이 반복됐다 (브랜드 86개)
-- 2) V4 에서 겹쳐 만든 중복 금지 인덱스를 지운다 (V1 에 같은 제약이 이미 있다)
-- 3) 여러 값을 한 칸에 넣던 areas 칸을 지운다 (일정은 지역을 좌표로 정한다)
-- 4) 식당을 가리키는데 외래키가 없던 두 표에 외래키를 건다
-- 5) 외래키 칸에 인덱스를 더한다 (PostgreSQL 은 외래키에 인덱스를 자동으로 만들지 않는다)

-- 1) 체인점 브랜드 -------------------------------------------------------

CREATE TABLE public.chain_brands (
    id         bigserial PRIMARY KEY,
    name       character varying(60) NOT NULL,
    name_ko    character varying(60),
    category   character varying(10) NOT NULL,
    genre_ko   character varying(30),
    budget_min integer,
    budget_max integer
);

CREATE UNIQUE INDEX uk_chain_brands_name ON public.chain_brands (name);

-- V4 의 체인점 칸은 아직 비어 있다 (가져오기 전)
ALTER TABLE public.restaurants
    DROP COLUMN brand,
    DROP COLUMN brand_ko,
    DROP COLUMN chain_category,
    DROP COLUMN genre_ko,
    ADD COLUMN brand_id bigint REFERENCES public.chain_brands(id);

CREATE INDEX idx_restaurants_brand ON public.restaurants (brand_id);

-- 2) 중복 인덱스 -------------------------------------------------------

DROP INDEX IF EXISTS public.uk_restaurants_store_id;
DROP INDEX IF EXISTS public.uk_attractions_place_id;

-- 3) areas 칸 ---------------------------------------------------------

ALTER TABLE public.restaurants DROP COLUMN areas;
ALTER TABLE public.attractions DROP COLUMN areas;

-- 4) 식당 외래키 ---------------------------------------------------------
-- 지금 DB 의 예전 행은 검사하지 않고(NOT VALID) 새로 들어오는 행부터 지킨다.
-- 확장 데이터를 넣을 때 여행·일정을 비운 뒤 VALIDATE 한다 (가져오기 코드)

DELETE FROM public.trip_excluded_restaurants WHERE restaurant_id IS NULL;
ALTER TABLE public.trip_excluded_restaurants ALTER COLUMN restaurant_id SET NOT NULL;

ALTER TABLE public.trip_excluded_restaurants
    ADD CONSTRAINT fk_trip_excluded_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) NOT VALID;

ALTER TABLE public.itinerary_stop_alternatives
    ADD CONSTRAINT fk_stop_alternative_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) NOT VALID;

-- 5) 외래키 인덱스 -------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_trips_user ON public.trips (user_id);
CREATE INDEX IF NOT EXISTS idx_trip_posters_trip ON public.trip_posters (trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_poster_pages_poster ON public.trip_poster_pages (poster_id);
CREATE INDEX IF NOT EXISTS idx_itinerary_stops_attraction ON public.itinerary_stops (attraction_id);
CREATE INDEX IF NOT EXISTS idx_itinerary_stops_restaurant ON public.itinerary_stops (restaurant_id);
CREATE INDEX IF NOT EXISTS idx_itinerary_day_dropped_day ON public.itinerary_day_dropped (day_id);
CREATE INDEX IF NOT EXISTS idx_itinerary_day_warnings_day ON public.itinerary_day_warnings (day_id);
CREATE INDEX IF NOT EXISTS idx_trip_interests_trip ON public.trip_interests (trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_preference_constraints_pref ON public.trip_preference_constraints (trip_preference_id);
CREATE INDEX IF NOT EXISTS idx_stop_alternatives_restaurant ON public.itinerary_stop_alternatives (restaurant_id);
