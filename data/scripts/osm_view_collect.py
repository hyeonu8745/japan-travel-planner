"""전망대·전망 타워·전망 지점 관광지를 OSM 에서 보충 수집한다 (10-18). 인터넷이 되는 PC에서 한 번 실행 (5~10분).

    프로젝트 루트에서:  python data/scripts/osm_view_collect.py

처음 관광지 수집(osm_collect.py)은 관광 지역 사각형 안의 tourism 태그만 받아서
전망 타워(man_made=tower)·도심 밖 산 전망대(모이와야마·롯코산 등)가 빠졌다.
여기서는 도시 중심 반경(data_clean.CITY_RADIUS) 안에서 다음을 받는다.
  - tourism=viewpoint 중 위키데이터가 있는 곳 (유명한 전망 지점)
  - man_made=tower 중 tower:type=observation 이거나 tourism 태그가 있는 곳 (전망 타워)
  - 이름에 展望台·展望室·展望デッキ·展望所·スカイデッキ 가 들어가고 tourism·man_made 태그가 있는 곳
출력  data/extra/osm_views.json  → data_clean.py 가 관광지(VIEW)에 합친다 (이미 있는 곳은 건너뜀)
다음  python data/scripts/data_clean.py → python data/scripts/extra_collect.py wiki (새 관광지 사진·한국어 소개)
"""
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from data_clean import CITY_RADIUS  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DST = ROOT / "data" / "extra" / "osm_views.json"
OVERPASS = "https://overpass-api.de/api/interpreter"
WIKIDATA = "https://www.wikidata.org/w/api.php"
UA = "TabiMate-student-project/1.0 (osm views)"
NAME_RE = "展望台|展望室|展望デッキ|展望所|展望ロビー|スカイデッキ"


def post_overpass(q, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(OVERPASS, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=200) as r:
                return json.loads(r.read().decode())["elements"]
        except Exception as e:  # noqa: BLE001
            if i == tries - 1:
                raise
            print(f"  다시 시도 {i + 1}/{tries - 1} ({e})")
            time.sleep(15 * (i + 1))


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def query(lat, lng, radius_km):
    a = f"(around:{int(radius_km * 1000)},{lat},{lng})"
    return f"""[out:json][timeout:180];
(
  nwr["tourism"="viewpoint"]["wikidata"]{a};
  nwr["man_made"="tower"]["tower:type"="observation"]{a};
  nwr["man_made"="tower"]["tourism"]{a};
  nwr["name"~"{NAME_RE}"]["tourism"]{a};
  nwr["name"~"{NAME_RE}"]["man_made"]{a};
);
out center tags;"""


def main():
    rows, seen = [], set()
    for n, (city, (lat, lng, radius)) in enumerate(CITY_RADIUS.items(), 1):
        try:
            els = post_overpass(query(lat, lng, radius))
        except Exception as e:  # noqa: BLE001
            print(f"[{n}/{len(CITY_RADIUS)}] {city} 실패: {e}")
            continue
        got = 0
        for e in els:
            key = f"{e['type']}/{e['id']}"
            t = e.get("tags", {})
            c = e.get("center") or {"lat": e.get("lat"), "lon": e.get("lon")}
            if key in seen or not t.get("name") or c.get("lat") is None:
                continue
            seen.add(key)
            rows.append({"osmId": key, "city": city, "name": t["name"], "nameJa": t.get("name:ja"), "nameEn": t.get("name:en"),
                         "nameKo": t.get("name:ko"), "lat": c["lat"], "lng": c["lon"], "wikidata": t.get("wikidata"),
                         "tags": {k: t[k] for k in ("tourism", "man_made", "tower:type", "natural", "opening_hours", "fee", "website") if k in t}})
            got += 1
        print(f"[{n}/{len(CITY_RADIUS)}] {city}: {got}곳")
        time.sleep(5)
    # 위키데이터 인기도(sitelinks 수)
    qids = sorted({r["wikidata"] for r in rows if r.get("wikidata")})
    links = {}
    for i in range(0, len(qids), 50):
        batch = qids[i:i + 50]
        try:
            data = get_json(WIKIDATA + "?" + urllib.parse.urlencode({"action": "wbgetentities", "ids": "|".join(batch),
                                                                       "props": "sitelinks", "format": "json", "formatversion": 2}))
            for q in batch:
                links[q] = len((data.get("entities", {}).get(q, {}) or {}).get("sitelinks", {}) or {})
        except Exception as e:  # noqa: BLE001
            print(f"  위키데이터 실패: {e}")
        time.sleep(1)
    for r in rows:
        r["sitelinks"] = links.get(r.get("wikidata"), 0)
    DST.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"저장: {DST} ({len(rows)}곳, 위키데이터 있음 {len(qids)})")


if __name__ == "__main__":
    main()
