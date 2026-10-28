"""유명 관광지 보충 수집 (10-19). 인터넷이 되는 PC에서 한 번 실행 (작은 칸으로 나눠 천천히, 1시간 반 안팎. 끊겨도 다시 실행하면 이어서).
공용 서버가 붐비면(502 at capacity) 기다리는 시간을 두 배씩 늘린다. 한국 시간 오전 7~10시(유럽 새벽)가 가장 한가하다.

    프로젝트 루트에서:  python data/scripts/osm_popular_collect.py

처음 관광지 수집(osm_collect.py)은 식당이 모인 지역 중심 반경 0.8~3km 만 받아서
금각사·은각사·헤이안 신궁·다자이후·이쓰쿠시마·가이유칸 같은 번화가 밖 대표 관광지가 빠졌다.
1) 도시 중심 반경(data_clean.CITY_RADIUS) 안에서 위키데이터가 붙은 관광지·절·신사·성·공원·시장 → 위키 링크 수(인기도)
2) data/scripts/must_see.tsv(꼭 가는 관광지 목록) 중 지금 관광지 데이터에 없는 곳을 이름으로 찾기 (시장·거리 등)
출력  data/extra/osm_popular.json  → data_clean.py 가 합친다 → extra_collect.py wiki 로 새 관광지 사진
"""
import json
import math
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from data_clean import CITY_RADIUS, city_of, km  # noqa: E402
from osm_collect import kind_of  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DST = ROOT / "data" / "extra" / "osm_popular.json"
MUST = ROOT / "data" / "scripts" / "must_see.tsv"
MUST_MANUAL = ROOT / "data" / "scripts" / "must_see_manual.tsv"
ATTR = ROOT / "data" / "processed" / "clean_attractions.json"
OVERPASS_SERVERS = [   # 한 곳이 응답 없으면 다음 서버로 (10-19: overpass-api.de 가 연결 시간 초과)
    "https://overpass-api.de/api/interpreter",
    # 10-26: private.coffee·kumi.systems 는 하루 종일 응답 없음(2분씩 시간만 씀) → 뺌. overpass-api.de 는 502 여도 조금 뒤 받아짐
]
WIKIDATA = "https://www.wikidata.org/w/api.php"
UA = "TabiMate-student-project/1.0 (osm popular attractions)"
PAUSE_QUERY = 8     # 요청 사이 쉬는 초 (칸이 작아 가볍다)
PAUSE_NAME = 8      # 이름 검색 사이
GRID_KM = 8         # 10-21: 4조각(반경 19km)도 시간 초과 → 8km 격자 칸(칸을 덮는 반경 약 5.7km)으로 잘게
QUERY_TIMEOUT = 90  # 서버가 오래 붙잡지 않게 (무거우면 빨리 실패하고 다음 칸·다음 서버로)
KEEP_TAGS = ("tourism", "historic", "amenity", "religion", "leisure", "shop", "highway", "place", "website", "opening_hours", "fee")


def key(s):
    return re.sub(r"[\s・･()（）「」]", "", unicodedata.normalize("NFKC", s or "")).lower()


def overpass(q, tries=10):
    """overpass-api.de 가 꽉 찼을 때(502) 잠깐 쉬고 다시. 20초 → 40 → 80 → 160 → 300초(최대)."""
    last = None
    for i in range(tries):
        server = OVERPASS_SERVERS[i % len(OVERPASS_SERVERS)]
        try:
            req = urllib.request.Request(server, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=QUERY_TIMEOUT + 30) as r:
                return json.loads(r.read().decode())["elements"]
        except Exception as e:  # noqa: BLE001
            last = e
            if i == tries - 1:
                break
            wait = min(300, 20 * 2 ** i)
            print(f"  다시 시도 {i + 1}/{tries - 1} ({server.split('/')[2]}: {str(e)[:50]}) → {wait}초 기다림")
            time.sleep(wait)
    raise last


def row_of(e, city):
    t = e.get("tags", {})
    c = e.get("center") or {"lat": e.get("lat"), "lon": e.get("lon")}
    if not t.get("name") or c.get("lat") is None:
        return None
    return {"osmId": f"{e['type']}/{e['id']}", "city": city, "name": t["name"], "nameJa": t.get("name:ja"),
            "nameEn": t.get("name:en"), "nameKo": t.get("name:ko"), "lat": c["lat"], "lng": c["lon"],
            "wikidata": t.get("wikidata"), "kind": kind_of(t), "tags": {k: t[k] for k in KEEP_TAGS if k in t}}


DLAT = GRID_KM / 111.0
DLNG = GRID_KM / (111.0 * math.cos(math.radians(36)))


def all_tiles():
    """모든 도시 원을 덮는 전역 격자 칸 (겹치는 도시 — 오사카·교토·고베·나라, 도쿄·요코하마·가마쿠라 — 는 칸을 같이 씀).
    {칸 이름: (중심 lat, lng, 반경km)}"""
    half = GRID_KM / 2 * math.sqrt(2)
    tiles = {}
    for lat, lng, r in CITY_RADIUS.values():
        for i in range(math.floor((lat - r / 111) / DLAT), math.ceil((lat + r / 111) / DLAT) + 1):
            for j in range(math.floor((lng - r / (111 * math.cos(math.radians(lat)))) / DLNG),
                           math.ceil((lng + r / (111 * math.cos(math.radians(lat)))) / DLNG) + 1):
                clat, clng = (i + 0.5) * DLAT, (j + 0.5) * DLNG
                if km((lat, lng), (clat, clng)) <= r + half:
                    tiles[f"{i}_{j}"] = (clat, clng, half)
    return tiles


def sweep_query(lat, lng, r):
    a = f"(around:{int(r * 1000)},{lat},{lng})"
    return f"""[out:json][timeout:{QUERY_TIMEOUT}][maxsize:67108864];
(
  nwr["wikidata"]["tourism"~"^(attraction|museum|viewpoint|theme_park|zoo|aquarium|gallery)$"]{a};
  nwr["wikidata"]["historic"~"^(castle|monument|memorial|ruins|archaeological_site|building|city_gate|temple|shrine)$"]{a};
  nwr["wikidata"]["amenity"~"^(place_of_worship|marketplace)$"]{a};
  nwr["wikidata"]["leisure"~"^(park|garden)$"]{a};
);
out center tags;"""


BAD_PICK = {"tourism": ("hotel", "hostel", "guest_house", "apartment", "motel"),
            "amenity": ("school", "kindergarten", "parking", "parking_entrance", "bus_station", "university", "college", "clinic"),
            "highway": ("bus_stop",)}


def score(r, pats=()):
    """이름으로 찾은 후보 중 고르기: 호텔·학교·주차장 아님 → 이름이 정확히 같음 → 위키데이터 → 관광 태그 → 면·관계
    (10-19: キャナルシティ 가 근처 호텔, ももち浜 이 초등학교로 잡혔던 것)"""
    t = r["tags"]
    bad = any(t.get(k) in v for k, v in BAD_PICK.items()) or "railway" in t
    exact = any(key(r["name"]) == key(p) for p in pats)
    return (not bad, exact, bool(r.get("wikidata")),
            any(k in t for k in ("tourism", "historic", "leisure", "shop")) or t.get("amenity") in ("place_of_worship", "marketplace"),
            r["osmId"].startswith(("relation", "way")))


def must_see_missing():
    attrs = json.loads(ATTR.read_text(encoding="utf-8")) if ATTR.exists() else []
    manual = MUST_MANUAL.read_text(encoding="utf-8").splitlines() if MUST_MANUAL.exists() else []
    manual = {tuple(l.split("\t")[:2]) for l in manual if l.strip() and not l.startswith("#")}   # 직접 넣은 곳은 찾지 않음
    out = []
    for line in MUST.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        city, pats, ko = (line.split("\t") + ["", ""])[:3]
        ps = [key(p) for p in pats.split("|")]
        if not any(a["city"] == city and any(p in key(a["name"]) + "|" + key(a.get("nameJa")) + "|" + key(a.get("nameEn")) for p in ps)
                   for a in attrs):
            if (city, pats) not in manual:
                out.append((city, pats, ko))
    return out


def save(data):
    DST.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


def main():
    """도시 하나·꼭 가는 관광지 한 줄마다 저장 (10-19: 마지막에만 저장해서 연결이 끊기자 다 잃었다). 다시 실행하면 받은 건 건너뜀."""
    data = json.loads(DST.read_text(encoding="utf-8")) if DST.exists() else {}
    data.setdefault("sweep", []); data.setdefault("mustSee", []); data.setdefault("doneCities", [])
    seen = {x["osmId"] for x in data["sweep"]}
    tiles = all_tiles()
    todo = [k for k in tiles if k not in data["doneCities"]]
    if "--links" in sys.argv:
        todo = []
    print(f"격자 칸 {len(tiles)}개 중 남은 칸 {len(todo)}개 (칸마다 저장)")
    for n, k in enumerate(todo, 1):
        plat, plng, pr = tiles[k]
        try:
            els = overpass(sweep_query(plat, plng, pr))
        except Exception as e:  # noqa: BLE001
            print(f"  칸 {k} 실패: {e} → 다시 실행하면 여기부터")
            return save(data)
        rows = []
        for e in els:
            c = e.get("center") or {"lat": e.get("lat"), "lon": e.get("lon")}
            city = city_of(c["lat"], c["lon"]) if c.get("lat") is not None else None
            x = row_of(e, city) if city else None
            if x and x["osmId"] not in seen:
                rows.append(x)
                seen.add(x["osmId"])
        data["sweep"] += rows
        data["doneCities"].append(k)
        save(data)
        if n % 10 == 0 or n == len(todo):
            print(f"  {n}/{len(todo)} 칸 (지금까지 {len(data['sweep'])}곳)")
        time.sleep(PAUSE_QUERY)
    done_rows = {(m["city"], m["pattern"]) for m in data["mustSee"] if m.get("pick") is not None or m.get("candidates") == 0}
    missing = [x for x in must_see_missing() if (x[0], x[1]) not in done_rows]
    if "--links" in sys.argv:   # 10-28: Overpass 가 막혀도 위키 링크 수(인기도)만 받기
        missing = []
    print(f"꼭 가는 관광지 중 데이터에 없는 곳 {len(missing)} → 이름으로 찾기")
    for city, pats, ko in missing:
        lat, lng, r = CITY_RADIUS[city]
        pat = "|".join(re.escape(p) for p in pats.split("|")).replace('"', '\\"')
        q = f'[out:json][timeout:120];nwr["name"~"{pat}"](around:{int(r * 1000)},{lat},{lng});out center tags 50;'
        try:
            cands = [x for x in (row_of(e, city) for e in overpass(q)) if x]
        except Exception as e:  # noqa: BLE001
            print(f"  {city} {ko} 실패: {e} → 다시 실행하면 이어서")
            continue
        ps = pats.split("|")
        good = [c for c in cands if score(c, ps)[0]]
        best = max(good, key=lambda c: score(c, ps)) if good else None
        data["mustSee"] = [m for m in data["mustSee"] if (m["city"], m["pattern"]) != (city, pats)]
        data["mustSee"].append({"city": city, "pattern": pats, "ko": ko, "pick": best, "candidates": len(cands)})
        save(data)
        print(f"  {city:11s} {ko:20s} → {best['name'] + ' ' + best['osmId'] if best else '못 찾음'}")
        time.sleep(PAUSE_NAME)
    # 위키 링크 수 (인기도)
    qids = sorted({x["wikidata"] for x in data["sweep"]} | {m["pick"]["wikidata"] for m in data["mustSee"] if m["pick"] and m["pick"].get("wikidata")})
    links = {}
    for i in range(0, len(qids), 50):
        batch = qids[i:i + 50]
        try:
            req = urllib.request.Request(WIKIDATA + "?" + urllib.parse.urlencode(
                {"action": "wbgetentities", "ids": "|".join(batch), "props": "sitelinks", "format": "json", "formatversion": 2}),
                headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                ents = json.loads(r.read().decode()).get("entities", {})
            for q in batch:
                links[q] = len((ents.get(q) or {}).get("sitelinks") or {})
        except Exception as e:  # noqa: BLE001
            print(f"  위키데이터 실패: {e}")
        if (i // 50) % 10 == 0:
            print(f"  인기도 {min(i + 50, len(qids))}/{len(qids)}")
        time.sleep(1)
    for x in data["sweep"] + [m["pick"] for m in data["mustSee"] if m["pick"]]:
        x["sitelinks"] = links.get(x.get("wikidata"), 0)
    save(data)
    print(f"저장: {DST} (위키데이터 관광지 {len(data['sweep'])}, 위키 링크 8 이상 {sum(1 for x in data['sweep'] if x['sitelinks'] >= 8)}, "
          f"이름으로 찾음 {sum(1 for m in data['mustSee'] if m['pick'])}/{len(data['mustSee'])})")


if __name__ == "__main__":
    main()
