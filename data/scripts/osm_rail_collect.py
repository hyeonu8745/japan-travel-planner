"""
OpenStreetMap(Overpass)에서 철도 노선망과 함께 쓸 데이터를 받는다 (10-16, 확장 계획 0-9). 키·가입 없음, 무료.

    프로젝트 루트에서:  python data/scripts/osm_rail_collect.py
    지역 하나만:        python data/scripts/osm_rail_collect.py tokyo
    정리만 다시:        python data/scripts/osm_rail_collect.py --build

받는 것 (지역마다, 범위를 조각으로 나눠서)
1. rail      : 노선 관계 route=subway/train/light_rail/monorail/tram/funicular/ferry (out meta → 마지막 수정 날짜)
               + 노선 안의 역(stop·stop_entry_only·stop_exit_only)·승강장(platform) 노드
2. station   : 역(railway=station / public_transport=station 노드), 갈아타기 묶음(public_transport=stop_area 관계)
3. aerial    : 케이블카·로프웨이·곤돌라(aerialway=*) 선과 승강장, 등산철도 선(railway=funicular)
4. extra     : 온천·센토(amenity=public_bath), 코인로커·짐 보관(amenity=locker/luggage_locker, luggage_storage),
               관광 안내소(tourism=information + information=office), 편의점(shop=convenience), ATM(amenity=atm)
5. wheel     : 휠체어 정보가 있는 관광지(wheelchair=* + tourism/historic/leisure/절·신사) → 관광지 osmId에 붙이기
6. bus       : 버스 노선(route=bus) + 정류장 — 교토만 (교토는 철도·버스 둘 다 많다)

출력 (모두 .gitignore, data/osm_rail/)
- raw/<지역>_<조각>_<종류>.json : Overpass 원본 조각 (받은 조각은 다음 실행에서 건너뜀 → 끊겨도 이어 받기)
- <지역>_routes.json      : 노선 [{id, route, ref, name, nameJa, nameEn, nameKo, colour, operator, network, from, to, master, timestamp, stops:[노드 id 순서], platforms:[...]}]
- <지역>_stops.json       : 노선 안 역·승강장 노드 {id: {name, nameJa, nameEn, nameKo, lat, lng, operator, railway, publicTransport, wheelchair}}
- <지역>_stations.json    : 역 노드 목록
- <지역>_stop_areas.json  : 갈아타기 묶음 [{id, name, members:[노드 id]}]
- <지역>_aerial.json      : 케이블카·로프웨이·등산철도 [{id, kind, name, nodes:[[lat,lng]...], ...}] + 승강장
- <지역>_extra.json       : {publicBath:[], locker:[], info:[], convenience:[], atm:[]}
- kyoto_bus_routes.json / kyoto_bus_stops.json
- attractions_wheelchair.json : {"way/123": "yes" | "limited" | "no"} (clean_attractions.json의 osmId와 같은 형식)
- rail_report.txt         : 지역별 개수, 역이 적은 노선, 3년 넘게 안 고친 노선, 이름이 달라 안 묶인 가까운 역 쌍(보정 표 초안)

출처 표시 의무: 화면에 "© OpenStreetMap contributors" (ODbL).
공용 서버라 요청 사이에 쉬고, 요청에 프로그램 이름(User-Agent)을 넣는다. 붐빌 때(504·429)는 서버를 바꿔 다시 묻는다.
"""
import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "data" / "osm_rail"
RAW = OUT / "raw"
ATTRACTIONS = ROOT / "data" / "processed" / "clean_attractions.json"
OVERPASS = [   # osm_collect.py와 같은 공용 서버 3곳 + 일본 OSM 재단 서버(일본 데이터만 있음, 10-16 추가)
    "https://overpass-api.de/api/interpreter",
    "https://overpass.osm.jp/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
FAIL_LIMIT_NEW = 2  # 이번 실행에서 한 번도 안 된 서버는 연속 2번 실패하면
FAIL_LIMIT_OK = 6   # 한 번이라도 된 서버는 연속 6번 실패해야 (가끔 502가 나도 계속 쓴다)
COOLDOWN = 600      # 이 초 동안 그 서버는 묻지 않는다 (10-16: kumi·private.coffee가 계속 500이라 시간만 버림)
_fails = {}         # 서버 → 연속 실패 수
_resting = {}       # 서버 → 다시 물어도 되는 시각
_ok = set()         # 이번 실행에서 받아 본 적 있는 서버 → 먼저 쓴다
UA = "TabiMate-student-project/1.0 (osm rail network)"
PAUSE = 30   # 받은 뒤 쉬는 초 (공용 서버는 무거운 요청 뒤 잠깐 거절한다 → 바로 물으면 3곳 다 실패, 10-16 확인)

# 지역: (남, 서, 북, 동), 조각 수(n → n×n). 공항선이 들어가게 공항까지 포함한다
REGIONS = {
    "tokyo":       ((35.25, 139.45, 35.85, 140.45), 3),   # 도쿄·요코하마·가마쿠라 + 나리타·하네다
    "hakone":      ((35.18, 138.95, 35.30, 139.17), 1),   # 등산철도·케이블카·로프웨이·유람선
    "kawaguchiko": ((35.45, 138.70, 35.56, 138.86), 1),
    "kansai":      ((34.38, 135.05, 35.08, 135.90), 3),   # 오사카·교토·고베·나라 + 간사이·고베 공항
    "fukuoka":     ((33.50, 130.30, 33.70, 130.50), 2),
    "sapporo":     ((42.75, 140.95, 43.25, 141.75), 2),   # 삿포로·오타루 + 신치토세 공항
    "nagoya":      ((34.84, 136.75, 35.30, 137.05), 2),   # 나고야 + 주부 공항
    "hiroshima":   ((34.25, 132.25, 34.50, 132.55), 2),   # 노면전차·미야지마 페리
    "naha":        ((26.15, 127.63, 26.25, 127.75), 1),   # 유이레일
    "kanazawa":    ((36.50, 136.58, 36.62, 136.72), 1),
    "takayama":    ((36.12, 137.22, 36.18, 137.30), 1),
    # 10-16 추가: 후쿠오카 공항으로 들어가는 지역 (osm_rail_collect_beppu_yufuin.py로 따로 받는다)
    "beppu":       ((33.22, 131.42, 33.37, 131.53), 1),   # 벳푸역·간나와 온천·벳푸 로프웨이
    "yufuin":      ((33.24, 131.33, 33.30, 131.40), 1),   # 유후인역·긴린코 (JR 규다이선)
}
KYOTO_BUS = ((34.92, 135.65, 35.08, 135.82), 2)   # 교토 시내버스만

RAIL_ROUTES = "subway|train|light_rail|monorail|tram|funicular|ferry"
STOP_ROLES = ("stop", "stop_entry_only", "stop_exit_only")
HEAD = "[out:json][timeout:180][maxsize:268435456];"   # 노선 관계는 멤버가 많아 osm_collect.py보다 크게 (180초·256MB)


# ===================== 범위 =====================

def tiles(box, n):
    s, w, nn, e = box
    dlat, dlng = (nn - s) / n, (e - w) / n
    return [f"{s + i * dlat:.5f},{w + j * dlng:.5f},{s + (i + 1) * dlat:.5f},{w + (j + 1) * dlng:.5f}"
            for i in range(n) for j in range(n)]


# ===================== 쿼리 =====================

def q_rail(b):
    return f"""{HEAD}
relation["type"="route"]["route"~"^({RAIL_ROUTES})$"]({b})->.r;
.r out meta;
(
  node(r.r:"stop"); node(r.r:"stop_entry_only"); node(r.r:"stop_exit_only"); node(r.r:"platform");
)->.s;
.s out body;
relation(br.r)["type"="route_master"]->.m;
.m out tags;"""


def q_station(b):
    return f"""{HEAD}
(
  node["railway"~"^(station|halt)$"]({b});
  node["public_transport"="station"]({b});
);
out body;
relation["public_transport"="stop_area"]({b});
out body;"""


def q_aerial(b):
    return f"""{HEAD}
(
  way["aerialway"~"^(cable_car|gondola|mixed_lift|chair_lift)$"]({b});
  way["railway"="funicular"]({b});
);
out tags geom;
node["aerialway"="station"]({b});
out body;"""


def q_extra(b):
    return f"""{HEAD}
(
  nwr["amenity"="public_bath"]({b});
  nwr["amenity"~"^(locker|luggage_locker)$"]({b});
  nwr["luggage_storage"]({b});
  nwr["shop"="luggage_storage"]({b});
  nwr["tourism"="information"]["information"="office"]({b});
  node["shop"="convenience"]({b});
  node["amenity"="atm"]({b});
);
out center tags;"""


def q_wheel(b):
    return f"""{HEAD}
(
  nwr["wheelchair"]["tourism"~"^(attraction|museum|viewpoint|theme_park|zoo|aquarium|gallery)$"]({b});
  nwr["wheelchair"]["historic"]({b});
  nwr["wheelchair"]["leisure"~"^(park|garden)$"]({b});
  nwr["wheelchair"]["amenity"="place_of_worship"]({b});
  nwr["wheelchair"]["shop"~"^(mall|department_store)$"]({b});
);
out tags;"""


def q_bus(b):
    return f"""{HEAD}
relation["type"="route"]["route"="bus"]({b})->.r;
.r out meta;
(node(r.r:"stop"); node(r.r:"platform"); node(r.r:"stop_entry_only"); node(r.r:"stop_exit_only");)->.s;
.s out body;"""


KINDS = {"rail": q_rail, "station": q_station, "aerial": q_aerial, "extra": q_extra, "wheel": q_wheel}


# ===================== 받기 =====================

def post(url, data):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=body, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as res:
        return json.loads(res.read().decode("utf-8"))


def _next_server():
    """쉬는 중이 아닌 서버 중 순서대로. 모두 쉬는 중이면 가장 먼저 풀리는 서버를 기다린다"""
    now = time.time()
    ready = sorted((s for s in OVERPASS if _resting.get(s, 0) <= now), key=lambda s: (s not in _ok, OVERPASS.index(s)))
    if ready:
        return ready[0]
    server = min(OVERPASS, key=lambda s: _resting[s])
    wait = int(_resting[server] - now) + 1
    print(f"    서버 모두 쉬는 중, {wait}초 기다림")
    time.sleep(wait)
    return server


def ask(q, label):
    """쿼리 하나를 최대 12번 시도. 받아 본 서버를 먼저 쓰고, 계속 실패하는 서버는 10분 동안 뺀다"""
    for attempt in range(12):
        server = _next_server()
        host = server.split('/')[2]
        try:
            part = post(server, {"data": q})
            remark = part.get("remark") or ""
            if "runtime error" in remark or "timed out" in remark or "out of memory" in remark:
                raise RuntimeError(remark[:60])
            _fails[server] = 0
            _ok.add(server)
            return part
        except Exception as e:
            reason = str(e)[:60]
            if isinstance(e, urllib.error.HTTPError):   # Overpass는 실패 이유를 본문에 적어 준다 (쿼리 오류인지 붐빔인지)
                try:
                    body = e.read().decode("utf-8", "replace")
                    detail = [line for line in body.splitlines() if "remark" in line.lower() or "runtime" in line.lower()]
                    reason += " / " + " ".join(detail)[:200] if detail else ""
                except Exception:
                    pass
            _fails[server] = _fails.get(server, 0) + 1
            if _fails[server] >= (FAIL_LIMIT_OK if server in _ok else FAIL_LIMIT_NEW):
                _resting[server] = time.time() + COOLDOWN
                _fails[server] = 0
                print(f"    {label} 실패({host}: {reason}) → {host}는 {COOLDOWN // 60}분 동안 빼요")
            else:
                print(f"    {label} 실패({host}: {reason}), 15초 뒤 다시")
            time.sleep(15)
    return None


def fetch(region, box, n, kinds):
    """조각 × 종류마다 파일 하나. 이미 받은 파일은 건너뛴다. 모두 받았으면 True"""
    ok = True
    parts = tiles(box, n)
    for t, b in enumerate(parts):
        for kind, make in kinds.items():
            f = RAW / f"{region}_{t}_{kind}.json"
            if f.exists():
                continue
            part = ask(make(b), f"{region} 조각{t + 1}/{len(parts)} {kind}")
            if part is None:
                ok = False
                continue
            f.write_text(json.dumps(part.get("elements", []), ensure_ascii=False), encoding="utf-8")
            print(f"  {region} 조각{t + 1}/{len(parts)} {kind}: {len(part.get('elements', []))}개")
            time.sleep(PAUSE)
    return ok


def load(region, kind):
    """조각을 합치고 같은 요소(조각 경계·여러 조각에 걸친 긴 노선)는 하나로"""
    seen, out = set(), []
    for f in sorted(RAW.glob(f"{region}_*_{kind}.json")):
        for e in json.loads(f.read_text(encoding="utf-8")):
            key = (e["type"], e["id"])
            if key not in seen:
                seen.add(key)
                out.append(e)
    return out


# ===================== 정리 =====================

def names(tags):
    return {"name": tags.get("name"), "nameJa": tags.get("name:ja"), "nameEn": tags.get("name:en"),
            "nameKo": tags.get("name:ko")}


def latlng(e):
    if "lat" in e:
        return e["lat"], e["lon"]
    c = e.get("center") or {}
    return c.get("lat"), c.get("lon")


def build_rail(region, elements):
    routes, stops, masters = [], {}, {}
    for e in elements:
        if e["type"] == "relation" and e.get("tags", {}).get("type") == "route_master":
            for m in e.get("members", []):
                masters[m["ref"]] = e["id"]
    for e in elements:
        tags = e.get("tags", {})
        if e["type"] == "node":
            lat, lng = latlng(e)
            stops[e["id"]] = {**names(tags), "lat": lat, "lng": lng, "operator": tags.get("operator"),
                              "railway": tags.get("railway"), "publicTransport": tags.get("public_transport"),
                              "wheelchair": tags.get("wheelchair")}
        elif e["type"] == "relation" and tags.get("type") == "route":
            members = e.get("members", [])
            routes.append({
                "id": e["id"], "route": tags.get("route"), "ref": tags.get("ref"), **names(tags),
                "colour": tags.get("colour"), "operator": tags.get("operator"), "network": tags.get("network"),
                "from": tags.get("from"), "to": tags.get("to"), "master": masters.get(e["id"]),
                "timestamp": e.get("timestamp"),
                "stops": [m["ref"] for m in members if m["type"] == "node" and m.get("role") in STOP_ROLES],
                "platforms": [m["ref"] for m in members if m.get("role") == "platform"],
            })
    return routes, stops


def build_station(elements):
    stations, areas = [], []
    for e in elements:
        tags = e.get("tags", {})
        if e["type"] == "node":
            lat, lng = latlng(e)
            stations.append({"id": e["id"], **names(tags), "lat": lat, "lng": lng, "operator": tags.get("operator"),
                             "railway": tags.get("railway"), "station": tags.get("station"),
                             "wheelchair": tags.get("wheelchair")})
        elif e["type"] == "relation":
            areas.append({"id": e["id"], **names(tags),
                          "members": [m["ref"] for m in e.get("members", []) if m["type"] == "node"]})
    return stations, areas


def build_aerial(elements):
    lines, stations = [], []
    for e in elements:
        tags = e.get("tags", {})
        if e["type"] == "way":
            lines.append({"id": e["id"], "kind": tags.get("aerialway") or tags.get("railway"), **names(tags),
                          "operator": tags.get("operator"), "duration": tags.get("duration"),
                          "nodes": [[p["lat"], p["lon"]] for p in e.get("geometry", [])]})
        elif e["type"] == "node":
            stations.append({"id": e["id"], **names(tags), "lat": e.get("lat"), "lng": e.get("lon")})
    return {"lines": lines, "stations": stations}


def build_extra(elements):
    out = {"publicBath": [], "locker": [], "info": [], "convenience": [], "atm": []}
    for e in elements:
        tags = e.get("tags", {})
        lat, lng = latlng(e)
        item = {"osmId": f"{e['type']}/{e['id']}", **names(tags), "lat": lat, "lng": lng,
                "brand": tags.get("brand"), "operator": tags.get("operator"), "openingHours": tags.get("opening_hours"),
                "fee": tags.get("fee"), "wheelchair": tags.get("wheelchair")}
        a, shop = tags.get("amenity"), tags.get("shop")
        if a == "public_bath":
            item["bathType"] = tags.get("bath:type")      # onsen / sento 등 (없을 수 있음)
            out["publicBath"].append(item)
        elif a in ("locker", "luggage_locker") or "luggage_storage" in tags or shop == "luggage_storage":
            out["locker"].append(item)
        elif tags.get("tourism") == "information":
            out["info"].append(item)
        elif shop == "convenience":
            out["convenience"].append(item)
        elif a == "atm":
            out["atm"].append(item)
    return out


def km(a, b):
    lat1, lng1, lat2, lng2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lng2 - lng1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(h))


def unpaired_stations(stations, areas, meters=400):
    """회사가 다르고 이름도 다른데 가까운(400m) 역 쌍 중 같은 갈아타기 묶음에 없는 것 → 보정 표 초안 (오사카역 ↔ 우메다역)"""
    grouped = defaultdict(set)
    for a in areas:
        for m in a["members"]:
            grouped[m].add(a["id"])
    st = [s for s in stations if s.get("lat") is not None and s.get("railway") in ("station", None)]
    pairs = []
    for i in range(len(st)):
        for j in range(i + 1, len(st)):
            a, b = st[i], st[j]
            if (a.get("name") or "") == (b.get("name") or "") or a.get("operator") == b.get("operator"):
                continue
            if abs(a["lat"] - b["lat"]) > 0.004 or abs(a["lng"] - b["lng"]) > 0.005:
                continue
            d = km((a["lat"], a["lng"]), (b["lat"], b["lng"])) * 1000
            if d <= meters and not (grouped[a["id"]] & grouped[b["id"]]):
                pairs.append((round(d), a.get("name"), a.get("operator"), b.get("name"), b.get("operator")))
    return sorted(pairs)


def build(region, report):
    rail = load(region, "rail")
    if not rail:
        report.append(f"[{region}] 받은 데이터 없음")
        return {}
    routes, stops = build_rail(region, rail)
    stations, areas = build_station(load(region, "station"))
    aerial = build_aerial(load(region, "aerial"))
    extra = build_extra(load(region, "extra"))
    write(f"{region}_routes.json", routes)
    write(f"{region}_stops.json", {str(k): v for k, v in stops.items()})
    write(f"{region}_stations.json", stations)
    write(f"{region}_stop_areas.json", areas)
    write(f"{region}_aerial.json", aerial)
    write(f"{region}_extra.json", extra)

    by_kind = defaultdict(int)
    for r in routes:
        by_kind[r["route"]] += 1
    few = [r for r in routes if len(r["stops"]) < 3 and r["route"] != "ferry"]
    now = datetime.now(timezone.utc)
    old = [r for r in routes if r.get("timestamp")
           and (now - datetime.fromisoformat(r["timestamp"].replace("Z", "+00:00"))).days > 365 * 3]
    pairs = unpaired_stations(stations, areas)
    report.append(f"[{region}] 노선 {len(routes)} ({dict(by_kind)}), 노선 안 역·승강장 {len(stops)}, 역 {len(stations)}, "
                  f"갈아타기 묶음 {len(areas)}, 케이블카 등 {len(aerial['lines'])}, "
                  f"온천 {len(extra['publicBath'])} · 로커 {len(extra['locker'])} · 안내소 {len(extra['info'])} · "
                  f"편의점 {len(extra['convenience'])} · ATM {len(extra['atm'])}")
    for r in few[:20]:
        report.append(f"    역이 적은 노선: {r.get('name')} ({r['route']}, 역 {len(r['stops'])})")
    for r in old[:20]:
        report.append(f"    3년 넘게 안 고친 노선: {r.get('name')} ({r['timestamp'][:10]})")
    for p in pairs[:40]:
        report.append(f"    안 묶인 가까운 역 {p[0]}m: {p[1]}({p[2]}) ↔ {p[3]}({p[4]})")
    return {"routes": len(routes), "few": len(few), "old": len(old), "pairs": len(pairs)}


def build_bus(report):
    rail = load("kyoto_bus", "bus")
    if not rail:
        return
    routes, stops = build_rail("kyoto_bus", rail)
    write("kyoto_bus_routes.json", routes)
    write("kyoto_bus_stops.json", {str(k): v for k, v in stops.items()})
    few = sum(1 for r in routes if len(r["stops"]) < 3)
    report.append(f"[교토 버스] 노선 {len(routes)}, 정류장 {len(stops)}, 정류장 3개 미만 노선 {few} "
                  f"(많으면 버스 경로는 품질이 낮다는 뜻)")


def build_wheelchair(report):
    values = {}
    for region in REGIONS:
        for e in load(region, "wheel"):
            v = e.get("tags", {}).get("wheelchair")
            if v:
                values[f"{e['type']}/{e['id']}"] = v
    write("attractions_wheelchair.json", values)
    matched = 0
    if ATTRACTIONS.exists():
        ids = {a.get("osmId") for a in json.loads(ATTRACTIONS.read_text(encoding="utf-8"))}
        matched = sum(1 for k in values if k in ids)
    report.append(f"[휠체어] 정보 있는 관광지 {len(values)}곳, 그중 우리 관광지(clean_attractions)와 맞는 곳 {matched}곳")


def write(name, data):
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


# ===================== 실행 =====================

def main():
    RAW.mkdir(parents=True, exist_ok=True)
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    only_build = "--build" in sys.argv
    regions = {k: v for k, v in REGIONS.items() if not args or k in args}
    want_bus = not args or "kyoto" in args or "kansai" in args

    if not only_build:
        failed = []
        for i, (region, (box, n)) in enumerate(regions.items(), 1):
            print(f"[{i}/{len(regions)}] {region} (조각 {n * n}개 × 5종류)")
            if not fetch(region, box, n, KINDS):
                failed.append(region)
        if want_bus:
            print("[교토 버스]")
            if not fetch("kyoto_bus", KYOTO_BUS[0], KYOTO_BUS[1], {"bus": q_bus}):
                failed.append("kyoto_bus")
        if failed:
            print(f"\n받지 못한 조각이 있어요: {failed} → 다시 실행하면 남은 조각만 받아요")

    report = [f"OSM 철도 등 정리 ({datetime.now():%Y-%m-%d %H:%M})"]
    for region in REGIONS:
        build(region, report)
    build_bus(report)
    build_wheelchair(report)
    (OUT / "rail_report.txt").write_text("\n".join(report), encoding="utf-8")
    print("\n" + "\n".join(report[:1] + [line for line in report[1:] if not line.startswith("    ")]))
    print(f"\n자세한 내용: {OUT / 'rail_report.txt'}")


if __name__ == "__main__":
    main()
