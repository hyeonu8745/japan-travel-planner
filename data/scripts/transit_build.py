"""
대중교통 노선 정리 (11-22). data/osm_rail 의 철도·지하철·노면전차·모노레일 노선과 data/extra/bus_* 버스 노선을
하나의 파일 data/processed/clean_transit.json 으로 합친다. 서버는 transit 프로필로 이 파일을 DB에 넣고,
일정 구간마다 가까운 역과 탈 노선을 대강 보여 준다 (시간표는 없다).

- 노선: 이름에서 방향 "(久里浜 → 東京)" 을 뗀 노선 이름(line), 종류(mode), 번호(ref), 회사, 색, 정차 순서(stops)
- 같은 노선이 여러 지역 파일에 있으면 한 번만 (OSM 아이디 기준). 배(ferry)는 뺀다
- 버스는 stops 가 비어 있으면 platforms 를 쓰고, backwardStops 가 있으면 반대 방향 노선을 하나 더 만든다
- 역 이름 "佐保小学校 (Saho Elementary School)" 은 일본어 이름과 영어 이름으로 나눈다

실행: python data/scripts/transit_build.py
"""
import glob
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA = os.path.join(ROOT, "data")
OUT = os.path.join(DATA, "processed", "clean_transit.json")

MODES = {"train", "subway", "light_rail", "monorail", "tram", "funicular", "bus", "trolleybus"}
DIRECTION = re.compile(r"\s*[(（][^()（）]*(→|=>|->|⇒|~|～|方面|行)[^()（）]*[)）]\s*$")
# "バスN65: 三島駅 => 元箱根港", "小72　小田原駅西口 => 久野車庫前" 의 방향 부분
DIRECTION_TAIL = re.compile(r"[:：\s\u3000]+[^:：\s\u3000]+\s*(→|=>|->|⇒)\s*[^:：]+$")
EN_SUFFIX = re.compile(r"^(.*?)\s*\(([A-Za-z0-9 .,'&\-/]+)\)\s*$")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def stop_name(raw):
    """'佐保小学校 (Saho Elementary School)' → ('佐保小学校', 'Saho Elementary School')"""
    name = (raw.get("nameJa") or raw.get("name") or "").strip()
    en = raw.get("nameEn")
    m = EN_SUFFIX.match(name)
    if m and m.group(1):
        name, en = m.group(1).strip(), en or m.group(2).strip()
    return name, en


def line_name(route):
    name = (route.get("nameJa") or route.get("name") or "").strip()
    line = DIRECTION_TAIL.sub("", DIRECTION.sub("", name)).strip() or name
    line = line.replace("Osaka Metor", "Osaka Metro")   # OSM 오타
    return line[:200], name[:200]


def main():
    stops, routes, seen = {}, [], set()
    stop_files = glob.glob(os.path.join(DATA, "osm_rail", "*_stops*.json")) + \
        glob.glob(os.path.join(DATA, "osm_rail", "*_bus_stops.json")) + \
        glob.glob(os.path.join(DATA, "extra", "bus_*_stops.json"))
    for path in sorted(set(stop_files)):
        for sid, raw in load(path).items():
            if raw.get("lat") is None or raw.get("lng") is None:
                continue
            name, en = stop_name(raw)
            if not name:
                continue
            stops.setdefault(str(sid), {"name": name[:100], "nameEn": (en or None) and en[:100],
                                        "nameKo": raw.get("nameKo"), "lat": raw["lat"], "lng": raw["lng"]})

    route_files = glob.glob(os.path.join(DATA, "osm_rail", "*_routes*.json")) + \
        glob.glob(os.path.join(DATA, "extra", "bus_*_routes.json"))
    for path in sorted(set(route_files)):
        for r in load(path):
            mode = r.get("route")
            if mode not in MODES or str(r["id"]) in seen:
                continue
            seen.add(str(r["id"]))
            line, name = line_name(r)
            if not line:
                continue
            base = {"mode": "bus" if mode == "trolleybus" else mode, "ref": r.get("ref"), "line": line, "name": name,
                    "nameEn": r.get("nameEn"), "operator": r.get("operator"), "colour": r.get("colour")}
            forward = [str(s) for s in (r.get("stops") or r.get("platforms") or []) if str(s) in stops]
            if len(forward) >= 2:
                routes.append({"id": str(r["id"]), **base, "stops": forward})
            backward = [str(s) for s in (r.get("backwardStops") or []) if str(s) in stops]
            if len(backward) >= 2:
                routes.append({"id": str(r["id"]) + ":back", **base, "stops": backward})

    used = {s for r in routes for s in r["stops"]}
    stops = {k: v for k, v in stops.items() if k in used}
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"stops": stops, "routes": routes}, f, ensure_ascii=False, indent=1)
    by_mode = {}
    for r in routes:
        by_mode[r["mode"]] = by_mode.get(r["mode"], 0) + 1
    print(f"stops={len(stops)} routes={len(routes)} {by_mode} -> {OUT}")


if __name__ == "__main__":
    main()
