-- 11-22: 대중교통 노선 (OSM·국토수치정보 철도·지하철·노면전차·모노레일 + 몇 도시 버스). 시간표는 없다.
-- 일정 구간마다 가까운 역과 탈 노선을 대강 보여 주는 데만 쓴다 (읽기 전용, transit 프로필로 통째로 넣는다).
-- 정차 순서는 노선마다 쉼표로 이은 역 아이디(stop_ids)로 둔다 (서버가 시작할 때 한 번에 읽어 메모리에서 찾는다)
CREATE TABLE public.transit_stops (
    id      VARCHAR(40)      PRIMARY KEY,
    name    VARCHAR(100)     NOT NULL,
    name_en VARCHAR(100),
    name_ko VARCHAR(100),
    lat     DOUBLE PRECISION NOT NULL,
    lng     DOUBLE PRECISION NOT NULL
);

CREATE TABLE public.transit_routes (
    id       VARCHAR(120) PRIMARY KEY,
    mode     VARCHAR(20)  NOT NULL,
    ref      VARCHAR(40),
    line     VARCHAR(200) NOT NULL,
    name     VARCHAR(200) NOT NULL,
    name_en  VARCHAR(200),
    operator VARCHAR(120),
    colour   VARCHAR(20),
    stop_ids TEXT         NOT NULL
);
