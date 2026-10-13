"""
타베로그 수집 원본(data/tabelog_raw/*.json)에 위도·경도를 붙인다 (국토지리원 주소 검색 API, 키 없음·무료).

    프로젝트 루트에서:  python data/scripts/tabelog_geocode.py

- 입력: data/tabelog_raw/<지역>_pXX-YY.json (같은 가게가 여러 파일에 있으면 하나로 합치고 지역 이름을 모두 남김)
- 출력: data/processed/tabelog_geocoded.json  (원본 항목 + storeId, areas, addressQuery, lat, lng, geoTitle, geoLevel, suspect)
- 캐시: data/processed/geocode_gsi_cache.csv  (물어본 주소 → 결과. 중간에 끊겨도 다시 실행하면 이어서 함)
- 주소 끝의 건물명·층(「なんばオリエンタルホテル 1F」)은 떼고 번지까지만 묻는다.
- 결과 주소가 우리 주소의 시·구와 다르면 버린다. 번지까지 못 찾고 동네(丁目) 단위만 찾은 곳은 geoLevel=town.
- 같은 지역 식당들의 중심에서 5km 넘게 떨어지면 suspect=true (가져올 때 확인용).
- 서버에 부담 주지 않게 1초에 2~3건만 묻는다.
"""
import csv
import json
import math
import re
import statistics
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "data" / "tabelog_raw"
OUT_DIR = ROOT / "data" / "processed"
OUT = OUT_DIR / "tabelog_geocoded.json"
CACHE = OUT_DIR / "geocode_gsi_cache.csv"
API = "https://msearch.gsi.go.jp/address-search/AddressSearch?q="
SLEEP = 0.4
SUSPECT_KM = 5.0
HEADERS = {"User-Agent": "TabiMate-student-project/1.0 (geocoding tabelog addresses)"}

FILE_RE = re.compile(r"^(?P<area>.+)_p\d{2}-\d{2}(?:_.*)?\.json$|^(?P<area2>.+)_p\d{2}_test\d+\.json$")
STORE_ID_RE = re.compile(r"/(\d{6,})/?$")
# 「…1-7-11」「…2丁目8-17」「…千日前2-8-17」 같은 번지 부분까지
BANCHI_RE = re.compile(r"^(.*?\d+(?:(?:条|丁目|番地|番|号|-)[東西南北]?\d*)*)")   # 「南4条西3丁目1-1」「京町3丁目187」「1-7-11」
PREF_RE = re.compile(r"^(東京都|北海道|京都府|大阪府|.{2,3}?県)")


def norm(s: str) -> str:
    """전각 숫자·하이픈을 반각으로, 공백 정리"""
    s = unicodedata.normalize("NFKC", s or "")
    s = s.replace("−", "-").replace("‐", "-").replace("ー", "-") if re.search(r"\dー\d", s) else s.replace("−", "-").replace("‐", "-")
    return re.sub(r"\s+", " ", s).strip()


def address_query(address: str) -> str:
    """주소에서 건물명·층을 떼고 번지까지만 남긴다 (「大阪府大阪府…」처럼 겹친 현 이름도 정리)"""
    a = re.sub(r"^(東京都|北海道|京都府|大阪府|.{2,3}?県)\1", r"\1", norm(address))
    # 「京都府伏見区…」처럼 시가 빠진 주소 → 京都市·大阪市를 넣는다
    a = re.sub(r"^京都府(?!京都市)(\S+?区)", r"京都府京都市\1", a)
    a = re.sub(r"^大阪府(?!大阪市|堺市)(\S+?区)", r"大阪府大阪市\1", a)
    # 교토 거리 이름 주소 「東山区下河原通八坂鳥居前下ル下河原町459」 → 「東山区下河原町459」 (국토지리원은 거리 표기를 못 읽음)
    street = re.sub(r"(区)\S*?(?:上る|上ル|下る|下ル|東入る|東入ル|西入る|西入ル|東入|西入)(?=\S)", r"\1", a)
    if not re.search(r"区[\d\s]", street) and not street.endswith("区"):   # 거리 표기를 떼고 나서 동네 이름이 남을 때만
        a = street
    first = a.split(" ")[0]
    m = BANCHI_RE.match(first)
    return m.group(1) if m else first


def place_key(address: str):
    """(현, 가장 작은 시·구·정·촌) — 결과 주소가 같은 곳인지 확인용. 「京都府伏見区」처럼 시가 빠진 주소도 맞춘다"""
    a = norm(address)
    pref = PREF_RE.match(a)
    rest = a[pref.end():] if pref else a
    m = re.match(r"(?:.+?市)?(.+?区)", rest) or re.match(r"(.+?[市])", rest) or re.match(r"(?:.+?郡)?(.+?[町村])", rest)
    return (pref.group(1) if pref else "", m.group(1) if m else "")


def same_place(key, title: str) -> bool:
    pref, place = key
    t = norm(title)
    if pref and not t.startswith(pref):
        return False
    if place:
        i = t.find(place)
        # 시·구 이름만 돌려준 결과(구 중심 좌표)는 받지 않는다
        return i >= 0 and len(t) > i + len(place)
    return True


def load_raw():
    shops = {}
    for f in sorted(RAW.glob("*.json")):
        m = FILE_RE.match(f.name)
        if not m:
            continue
        area = m.group("area") or m.group("area2")
        for item in json.loads(f.read_text(encoding="utf-8")):
            url = item.get("restaurantUrl") or ""
            sid = STORE_ID_RE.search(url.rstrip("/") + "/")
            key = sid.group(1) if sid else url
            if not key:
                continue
            if key not in shops:
                shops[key] = dict(item, storeId=key, areas=[])
            if area not in shops[key]["areas"]:
                shops[key]["areas"].append(area)
    return shops


def load_cache():
    cache = {}
    if CACHE.exists():
        with CACHE.open(encoding="utf-8", newline="") as fp:
            for row in csv.DictReader(fp):
                cache[row["query"]] = row
    return cache


def save_cache_row(row):
    new = not CACHE.exists()
    with CACHE.open("a", encoding="utf-8", newline="") as fp:
        w = csv.DictWriter(fp, fieldnames=["query", "lat", "lng", "title", "status"])
        if new:
            w.writeheader()
        w.writerow(row)


def ask_gsi(query: str):
    req = urllib.request.Request(API + urllib.parse.quote(query), headers=HEADERS)
    with urllib.request.urlopen(req, timeout=15) as res:
        data = json.loads(res.read().decode("utf-8"))
    if not data:
        return None
    best = data[0]
    lng, lat = best["geometry"]["coordinates"]
    return {"lat": lat, "lng": lng, "title": best["properties"].get("title", "")}


def geo_level(title: str) -> str:
    return "banchi" if re.search(r"(番|号|\d+-\d+)", norm(title)) else "town"


def km(a, b):
    lat1, lng1, lat2, lng2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lng2 - lng1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(h))


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    shops = load_raw()
    cache = load_cache()
    queries = {}
    for s in shops.values():
        q = address_query(s.get("address") or "")
        s["addressQuery"] = q
        if q:
            queries[q] = place_key(q)
    todo = [q for q in queries if q not in cache]
    print(f"가게 {len(shops)}곳, 주소 {len(queries)}개 중 새로 물을 주소 {len(todo)}개 (약 {len(todo) * SLEEP / 60:.0f}분)")

    for i, q in enumerate(todo, 1):
        row = {"query": q, "lat": "", "lng": "", "title": "", "status": ""}
        try:
            r = ask_gsi(q)
            if r is None:
                row["status"] = "NOT_FOUND"
            else:
                row.update(lat=r["lat"], lng=r["lng"], title=r["title"])
                row["status"] = "OK" if same_place(queries[q], r["title"]) else "CITY_MISMATCH"
        except Exception as e:   # 네트워크 오류는 캐시에 남기지 않는다 → 다음 실행에서 다시 묻는다
            print(f"  오류 {q}: {e}")
            time.sleep(3)
            continue
        cache[q] = row
        save_cache_row(row)
        if i % 200 == 0 or i == len(todo):
            print(f"  {i}/{len(todo)}")
        time.sleep(SLEEP)

    # 결과 붙이기
    ok = town = fail = 0
    for s in shops.values():
        c = cache.get(s["addressQuery"])
        # 캐시의 판정은 예전 규칙일 수 있어서, 결과 주소로 다시 확인한다
        if c and c["lat"] and same_place(place_key(s["addressQuery"]), c["title"]):
            s["lat"], s["lng"], s["geoTitle"] = float(c["lat"]), float(c["lng"]), c["title"]
            s["geoLevel"] = geo_level(c["title"])
            ok += 1
            town += s["geoLevel"] == "town"
        else:
            s["lat"] = s["lng"] = None
            s["geoTitle"] = c["title"] if c else ""
            s["geoLevel"] = ("CITY_MISMATCH" if c and c["lat"] else c["status"] if c else "NO_ADDRESS")
            fail += 1

    # 지역 중심에서 너무 먼 곳 표시
    by_area = defaultdict(list)
    for s in shops.values():
        if s["lat"] is not None:
            by_area[s["areas"][0]].append((s["lat"], s["lng"]))
    centers = {a: (statistics.median(p[0] for p in pts), statistics.median(p[1] for p in pts)) for a, pts in by_area.items()}
    suspect = 0
    for s in shops.values():
        s["suspect"] = bool(s["lat"] is not None and km((s["lat"], s["lng"]), centers[s["areas"][0]]) > SUSPECT_KM)
        suspect += s["suspect"]

    OUT.write_text(json.dumps(list(shops.values()), ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"완료: 좌표 {ok}곳 (번지까지 {ok - town}, 동네 단위 {town}), 실패 {fail}곳, 중심에서 {SUSPECT_KM:.0f}km 넘는 곳 {suspect}곳")
    print(f"저장: {OUT}")


if __name__ == "__main__":
    sys.exit(main())
