-- V4: 도시 확장 데이터 구조 (11-16)
-- 식당: 도시·출처·휴일 정보·가게 특징, 체인점(브랜드) 칸
-- 관광지: OSM·위키데이터 칸(도시·종류·인기도), 야경·꼭 가는 곳, 운영 정보·야간 정보·사진(위키미디어)
-- 편의 시설: 새 테이블 (숙소 주변 안내·코인로커 등)
-- 기존 행은 그대로 두고 칸만 더한다. 새 데이터는 가져오기 코드가 채운다

-- 식당 ------------------------------------------------------------------

-- 가까운 역 글자는 길이가 700자를 넘는 것이 있다
ALTER TABLE public.restaurants ALTER COLUMN nearest_station TYPE text;

ALTER TABLE public.restaurants
    ADD COLUMN city               character varying(20),
    ADD COLUMN areas              character varying(200),
    ADD COLUMN source             character varying(10),
    ADD COLUMN business_hours     text,
    ADD COLUMN geo_level          character varying(10),
    ADD COLUMN location_suspect   boolean,
    ADD COLUMN closed_days        character varying(40),
    ADD COLUMN closed_irregular   boolean,
    ADD COLUMN no_regular_holiday boolean,
    ADD COLUMN closed_on_holidays boolean,
    ADD COLUMN reservation_only   boolean,
    ADD COLUMN smoking_all_seats  boolean,
    ADD COLUMN korean_menu        boolean,
    ADD COLUMN hyakumeiten        boolean,
    ADD COLUMN award_medal        character varying(10),
    ADD COLUMN standing_bar       boolean,
    ADD COLUMN brand              character varying(100),
    ADD COLUMN brand_ko           character varying(100),
    ADD COLUMN chain_category     character varying(10),
    ADD COLUMN genre_ko           character varying(50),
    ADD COLUMN opening_hours      character varying(200);

-- 예전 행(도쿄 캐글)은 출처를 채워 둔다. 가져오기를 다시 하면 어차피 지워진다
UPDATE public.restaurants SET source = 'KAGGLE', city = 'TOKYO' WHERE source IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_restaurants_store_id ON public.restaurants (store_id);
CREATE INDEX IF NOT EXISTS idx_restaurants_city ON public.restaurants (city);
CREATE INDEX IF NOT EXISTS idx_restaurants_source ON public.restaurants (source);

-- 관광지 ----------------------------------------------------------------

ALTER TABLE public.attractions
    ADD COLUMN osm_id                 character varying(40),
    ADD COLUMN city                   character varying(20),
    ADD COLUMN areas                  character varying(200),
    ADD COLUMN kind                   character varying(20),
    ADD COLUMN name_en                character varying(200),
    ADD COLUMN wikidata               character varying(20),
    ADD COLUMN popularity             integer,
    ADD COLUMN opening_hours          character varying(300),
    ADD COLUMN website                character varying(300),
    ADD COLUMN fee                    character varying(30),
    ADD COLUMN history_note           character varying(300),
    ADD COLUMN night_view             boolean,
    ADD COLUMN must_see               boolean,
    ADD COLUMN night_kind             character varying(20),
    ADD COLUMN night_hours            character varying(100),
    ADD COLUMN night_fee              character varying(50),
    ADD COLUMN night_closed           character varying(50),
    ADD COLUMN night_url              character varying(200),
    ADD COLUMN official_status        character varying(20),
    ADD COLUMN official_reservation   character varying(100),
    ADD COLUMN official_closed_days   character varying(60),
    ADD COLUMN official_hours         character varying(100),
    ADD COLUMN official_fee           character varying(60),
    ADD COLUMN official_url           character varying(200),
    ADD COLUMN photo_thumb            character varying(1000),
    ADD COLUMN photo_width            integer,
    ADD COLUMN photo_height           integer,
    ADD COLUMN photo_credit           character varying(1000),
    ADD COLUMN photo_license          character varying(50),
    ADD COLUMN photo_license_url      character varying(200),
    ADD COLUMN photo_artist           character varying(1000),
    ADD COLUMN photo_source           character varying(20);

UPDATE public.attractions SET city = 'TOKYO' WHERE city IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_attractions_place_id ON public.attractions (place_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_attractions_osm_id ON public.attractions (osm_id);
CREATE INDEX IF NOT EXISTS idx_attractions_city ON public.attractions (city);

-- 편의 시설 -------------------------------------------------------------

CREATE TABLE public.facilities (
    id            bigserial PRIMARY KEY,
    osm_id        character varying(40) NOT NULL,
    type          character varying(20) NOT NULL,
    city          character varying(20) NOT NULL,
    name          character varying(100),
    name_ja       character varying(100),
    name_en       character varying(100),
    name_ko       character varying(100),
    location      public.geometry(Point,4326) NOT NULL,
    operator      character varying(100),
    opening_hours character varying(200),
    fee           character varying(100),
    wheelchair    character varying(15),
    brand         character varying(60),
    network       character varying(40),
    ref           character varying(60),
    level         character varying(60),
    capacity      character varying(20),
    parking       character varying(20),
    maxstay       character varying(40),
    charge        character varying(200),
    bath_type     character varying(15)
);

CREATE UNIQUE INDEX uk_facilities_osm_id ON public.facilities (osm_id);
CREATE INDEX idx_facilities_location ON public.facilities USING gist (location);
CREATE INDEX idx_facilities_city_type ON public.facilities (city, type);
