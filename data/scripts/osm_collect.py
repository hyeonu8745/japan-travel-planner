"""
OpenStreetMap(Overpass) + Wikidata 로 관광지와 체인점을 받는다. 키·가입 없음, 무료.
tabelog_geocode.py 를 먼저 돌려야 한다 (지역 중심·범위를 식당 좌표로 정함).

    프로젝트 루트에서:  python data/scripts/osm_collect.py

입력
- data/processed/tabelog_geocoded.json : 지역별 식당 좌표 → 지역 중심(중앙값)과 반경(식당의 90% 가 들어가는 거리, 0.8~3km)
- 도쿄 9개 구는 고정 중심 + 반경 2.5km (지금 6개 구 + 스미다·고토·도시마)

출력 (모두 .gitignore)
- data/processed/osm_raw_<지역>.json   : Overpass 원본 (받은 지역은 다음 실행에서 건너뜀)
- data/processed/osm_attractions.json  : 관광지 (종류, 위키데이터 인기도 sitelinks, 한국어 이름)
- data/processed/osm_chains.json       : 브랜드가 붙은 식당·패스트푸드·카페 (체인점 후보)
- data/processed/osm_brand_candidates.csv : 브랜드별 개수 → 사전에 넣을 브랜드를 고르는 표

출처 표시 의무: 화면에 "© OpenStreetMap contributors" (ODbL). Wikidata 는 CC0.
공용 서버라 지역 사이에 몇 초씩 쉬고, 요청에 프로그램 이름(User-Agent)을 넣는다.
"""
import csv
import json
import math
import statistics
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROC = ROOT / "data" / "processed"
GEOCODED = PROC / "tabelog_geocoded.json"
OVERPASS = [   # 공용 서버. 한 곳이 바쁘면(504·429) 다음 서버로
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
WIKIDATA = "https://query.wikidata.org/sparql"
UA = "TabiMate-student-project/1.0 (osm attractions & chains)"
PAUSE = 5          # 지역 사이 쉬는 초
MIN_R, MAX_R = 800, 3000

TOKYO = {   # 구 중심(대략) — 반경 2.5km, 겹치는 곳은 하나로 합친다
    "tokyo_chiyoda": (35.6940, 139.7536), "tokyo_chuo": (35.6707, 139.7720), "tokyo_minato": (35.6581, 139.7514),
    "tokyo_shinjuku": (35.6938, 139.7034), "tokyo_shibuya": (35.6640, 139.6982), "tokyo_taito": (35.7126, 139.7800),
    "tokyo_sumida": (35.7107, 139.8015), "tokyo_koto": (35.6600, 139.8000), "tokyo_toshima": (35.7289, 139.7160),
}
TOKYO_R = 2500


def km(a, b):
    lat1, lng1, lat2, lng2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lng2 - lng1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(h))


def areas_from_restaurants():
    shops = json.loads(GEOCODED.read_text(encoding="utf-8"))
    pts = defaultdict(list)
    for s in shops:
        if s.get("lat") is not None and not s.get("suspect"):
            pts[s["areas"][0]].append((s["lat"], s["lng"]))
    areas = {}
    for name, p in pts.items():
        c = (statistics.median(x[0] for x in p), statistics.median(x[1] for x in p))
        d = sorted(km(c, x) * 1000 for x in p)
        r = d[int(len(d) * 0.9) - 1] if len(d) >= 10 else MAX_R
        areas[name] = (c[0], c[1], int(min(MAX_R, max(MIN_R, r))))
    for name, (lat, lng) in TOKYO.items():
        areas[name] = (lat, lng, TOKYO_R)
    return areas


def bbox(lat, lng, r):
    """반경 대신 사각형(남,서,북,동) — around 보다 서버 부담이 훨씬 적다"""
    dlat = r / 111_000
    dlng = r / (111_000 * math.cos(math.radians(lat)))
    return f"{lat - dlat:.5f},{lng - dlng:.5f},{lat + dlat:.5f},{lng + dlng:.5f}"


def tiles(lat, lng, r):
    """범위를 2x2 로 나눈 작은 사각형 4개 (큰 번화가는 한 번에 물으면 시간 초과)"""
    dlat = r / 111_000
    dlng = r / (111_000 * math.cos(math.radians(lat)))
    out = []
    for i in range(2):
        for j in range(2):
            s_, w_ = lat - dlat + i * dlat, lng - dlng + j * dlng
            out.append(f"{s_:.5f},{w_:.5f},{s_ + dlat:.5f},{w_ + dlng:.5f}")
    return out


def overpass_queries(lat, lng, r):
    """관광지·체인점을 나누고, 범위도 4조각으로 나눠 작은 쿼리 8개로 묻는다"""
    return [q for t in tiles(lat, lng, r) for q in _queries_for(f"({t})")]


def _queries_for(b):
    head = "[out:json][timeout:25][maxsize:67108864];"   # 작게 선언해야 붐빌 때도 순서가 빨리 온다
    attractions = f"""{head}
(
  nwr["tourism"~"^(attraction|museum|viewpoint|theme_park|zoo|aquarium|gallery)$"]{b};
  nwr["historic"~"^(castle|monument|memorial|ruins|archaeological_site|building|city_gate)$"]["wikidata"]{b};
  nwr["amenity"="place_of_worship"]["wikidata"]{b};
  nwr["leisure"~"^(park|garden)$"]["wikidata"]{b};
  nwr["shop"~"^(mall|department_store)$"]["wikidata"]{b};
);
out center tags;"""
    chains = f"""{head}
nwr["brand"]["amenity"~"^(restaurant|fast_food|cafe|food_court)$"]{b};
out center tags;"""
    return [attractions, chains]


def post(url, data: dict, accept="application/json"):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=body, headers={"User-Agent": UA, "Accept": accept})
    with urllib.request.urlopen(req, timeout=180) as res:
        return json.loads(res.read().decode("utf-8"))


def ask(q, label):
    """쿼리 하나를 서버를 돌려 가며 최대 9번 시도"""
    for attempt in range(9):
        server = OVERPASS[attempt % len(OVERPASS)]
        try:
            part = post(server, {"data": q})
            if part.get("remark") and ("runtime error" in part["remark"] or "timed out" in part["remark"]):
                raise RuntimeError(part["remark"][:60])
            return part
        except Exception as e:
            wait = 5 if (attempt + 1) % len(OVERPASS) else 30
            print(f"    {label} 실패({server.split('/')[2]}: {str(e)[:50]}), {wait}초 뒤 다른 서버로")
            time.sleep(wait)
    return None


def fetch_areas(areas):
    """지역마다 작은 쿼리 8개. 조각마다 파일로 남겨서 끊겨도 받은 조각은 다시 묻지 않는다"""
    for i, (name, (lat, lng, r)) in enumerate(sorted(areas.items()), 1):
        out = PROC / f"osm_raw_{name}.json"
        if out.exists():
            continue
        queries = overpass_queries(lat, lng, r)
        ok = True
        for k, q in enumerate(queries):
            part_file = PROC / f"osm_part_{name}_{k}.json"
            if part_file.exists():
                continue
            part = ask(q, f"{name} 조각{k + 1}/{len(queries)}")
            if part is None:
                ok = False
                continue
            part_file.write_text(json.dumps(part.get("elements", []), ensure_ascii=False), encoding="utf-8")
            time.sleep(2)
        if not ok:
            print(f"  {name} 일부 조각 실패 → 다시 실행하면 남은 조각만 받아요")
            continue
        seen, elements = set(), []
        for k in range(len(queries)):
            for e in json.loads((PROC / f"osm_part_{name}_{k}.json").read_text(encoding="utf-8")):
                if (e["type"], e["id"]) not in seen:   # 조각 경계에 걸친 건 하나만
                    seen.add((e["type"], e["id"]))
                    elements.append(e)
        out.write_text(json.dumps({"elements": elements}, ensure_ascii=False), encoding="utf-8")
        print(f"[{i}/{len(areas)}] {name} 반경 {r}m: {len(elements)}개")
        time.sleep(PAUSE)


KIND_ORDER = [
    ("tourism", "museum", "MUSEUM"), ("tourism", "gallery", "MUSEUM"), ("tourism", "viewpoint", "VIEW"),
    ("tourism", "theme_park", "THEME"), ("tourism", "zoo", "THEME"), ("tourism", "aquarium", "THEME"),
    ("historic", "castle", "CASTLE"), ("leisure", "park", "PARK"), ("leisure", "garden", "PARK"),
    ("shop", "mall", "SHOPPING"), ("shop", "department_store", "SHOPPING"),
]


def kind_of(tags):
    if tags.get("amenity") == "place_of_worship":
        rel = tags.get("religion")
        return "SHRINE" if rel == "shinto" else "TEMPLE" if rel == "buddhist" else "WORSHIP"
    for k, v, kind in KIND_ORDER:
        if tags.get(k) == v:
            return kind
    if "historic" in tags:
        return "HISTORIC"
    return "ATTRACTION"


def latlng(e):
    if "lat" in e:
        return e["lat"], e["lon"]
    c = e.get("center") or {}
    return c.get("lat"), c.get("lon")


def split_elements(areas):
    attractions, chains = {}, {}
    for name in sorted(areas):
        f = PROC / f"osm_raw_{name}.json"
        if not f.exists():
            continue
        for e in json.loads(f.read_text(encoding="utf-8")).get("elements", []):
            tags = e.get("tags", {})
            lat, lng = latlng(e)
            if lat is None or not (tags.get("name") or tags.get("brand")):
                continue
            key = f"{e['type']}/{e['id']}"
            is_food = tags.get("amenity") in ("restaurant", "fast_food", "cafe", "food_court")
            target = chains if is_food and tags.get("brand") else attractions
            if is_food and not tags.get("brand"):
                continue
            if key not in target:
                base = {"osmId": key, "lat": lat, "lng": lng, "areas": [],
                        "name": tags.get("name") or tags.get("brand"), "nameJa": tags.get("name:ja"),
                        "nameEn": tags.get("name:en"), "nameKoOsm": tags.get("name:ko"),
                        "openingHours": tags.get("opening_hours"), "wikidata": tags.get("wikidata")}
                if target is chains:
                    base.update(brand=tags.get("brand"), brandWikidata=tags.get("brand:wikidata"),
                                amenity=tags.get("amenity"), cuisine=tags.get("cuisine"))
                else:
                    base.update(kind=kind_of(tags), tags={k: v for k, v in tags.items()
                                if k in ("tourism", "historic", "amenity", "religion", "leisure", "shop", "website", "fee")})
                target[key] = base
            if name not in target[key]["areas"]:
                target[key]["areas"].append(name)
    return list(attractions.values()), list(chains.values())


def wikidata_info(qids):
    """sitelinks 수(인기도), 한국어 라벨, 한국어 위키백과 제목"""
    info = {}
    qids = sorted({q for q in qids if q and q.startswith("Q")})
    for i in range(0, len(qids), 200):
        values = " ".join(f"wd:{q}" for q in qids[i:i + 200])
        query = f"""
SELECT ?item ?sitelinks ?ko ?kowiki ?coord WHERE {{
  VALUES ?item {{ {values} }}
  ?item wikibase:sitelinks ?sitelinks .
  OPTIONAL {{ ?item wdt:P625 ?coord }}
  OPTIONAL {{ ?item rdfs:label ?ko FILTER(LANG(?ko) = "ko") }}
  OPTIONAL {{ ?a schema:about ?item ; schema:isPartOf <https://ko.wikipedia.org/> ; schema:name ?kowiki }}
}}"""
        for attempt in range(3):
            try:
                data = post(WIKIDATA, {"query": query, "format": "json"}, accept="application/sparql-results+json")
                for b in data["results"]["bindings"]:
                    q = b["item"]["value"].rsplit("/", 1)[-1]
                    info[q] = {"sitelinks": int(b["sitelinks"]["value"]),
                               "ko": (b.get("kowiki") or b.get("ko") or {}).get("value"),
                               # 좌표가 없는 항목 = 장소가 아님(기념비에 사람의 위키데이터가 붙은 경우 등)
                               "place": "coord" in b}
                break
            except Exception as e:
                print(f"  Wikidata 실패({e}), 다시")
                time.sleep(20 * (attempt + 1))
        print(f"  Wikidata {min(i + 200, len(qids))}/{len(qids)}")
        time.sleep(2)
    return info


def main():
    if not GEOCODED.exists():
        sys.exit("먼저 python data/scripts/tabelog_geocode.py 를 돌려 주세요 (tabelog_geocoded.json 이 없어요)")
    areas = areas_from_restaurants()
    print(f"지역 {len(areas)}곳 (타베로그 {len(areas) - len(TOKYO)} + 도쿄 {len(TOKYO)})")
    fetch_areas(areas)
    attractions, chains = split_elements(areas)

    info = wikidata_info([a["wikidata"] for a in attractions] + [c["brandWikidata"] for c in chains])
    for a in attractions:
        w = info.get(a["wikidata"] or "", {})
        a["wikidataIsPlace"] = bool(w.get("place"))
        # 「グラント将軍植樹碑」→ 그랜트 대통령(사람)처럼 장소가 아닌 위키데이터는 인기도·한국어 이름에 쓰지 않는다
        a["sitelinks"] = w.get("sitelinks", 0) if w.get("place") else 0
        a["nameKo"] = a["nameKoOsm"] or (w.get("ko") if w.get("place") else None)
    for c in chains:
        c["brandKo"] = info.get(c["brandWikidata"] or "", {}).get("ko")
    attractions.sort(key=lambda a: -a["sitelinks"])

    (PROC / "osm_attractions.json").write_text(json.dumps(attractions, ensure_ascii=False, indent=1), encoding="utf-8")
    (PROC / "osm_chains.json").write_text(json.dumps(chains, ensure_ascii=False, indent=1), encoding="utf-8")

    brands = defaultdict(lambda: {"count": 0, "amenity": Counter(), "cuisine": Counter(), "areas": set()})
    for c in chains:
        b = brands[(c["brand"], c["brandWikidata"] or "", c["brandKo"] or "")]
        b["count"] += 1
        b["amenity"][c["amenity"]] += 1
        b["cuisine"][c["cuisine"] or ""] += 1
        b["areas"].update(c["areas"])
    with (PROC / "osm_brand_candidates.csv").open("w", encoding="utf-8-sig", newline="") as fp:
        w = csv.writer(fp)
        w.writerow(["use(Y/N)", "brand", "brand_ko", "brand_wikidata", "count", "areas", "amenity", "cuisine"])
        for (brand, qid, ko), b in sorted(brands.items(), key=lambda x: -x[1]["count"]):
            w.writerow(["", brand, ko, qid, b["count"], len(b["areas"]),
                        b["amenity"].most_common(1)[0][0], b["cuisine"].most_common(1)[0][0]])

    with_wd = sum(1 for a in attractions if a["wikidata"])
    print(f"관광지 {len(attractions)}곳 (위키데이터 연결 {with_wd}, 한국어 이름 {sum(1 for a in attractions if a['nameKo'])}), "
          f"종류 {dict(Counter(a['kind'] for a in attractions).most_common())}")
    print(f"체인점 {len(chains)}곳, 브랜드 {len(brands)}개 → osm_brand_candidates.csv 에서 넣을 브랜드에 Y 표시")


if __name__ == "__main__":
    main()
