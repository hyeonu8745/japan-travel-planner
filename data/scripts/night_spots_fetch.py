"""야경 명소 좌표를 OSM(Overpass)에서 찾는다. 인터넷이 되는 PC에서 한 번 실행 (1~2분).

    프로젝트 루트에서:  python data/scripts/night_spots_fetch.py

입력  data/scripts/night_spots_osm.tsv  (city, 이름, OSM 이름 패턴)
출력  data/extra/night_spots_osm.json   (이름마다 고른 1곳 + 후보 목록)  → data_clean.py 가 읽는다

다시 실행하면 이미 찾은 곳(패턴이 같은 곳)은 건너뛰고, 못 찾았거나 패턴을 바꾼 곳만 다시 찾는다.
"""
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from data_clean import CITY_RADIUS, km  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
URL = "https://overpass-api.de/api/interpreter"
GOOD_TAGS = ("tourism", "man_made", "leisure", "natural", "historic", "place")


def query_retry(pattern, lat, lng, radius_km, tries=3):
    """Overpass 가 바쁠 때(502·504·429) 10·20·30초 쉬고 다시."""
    for i in range(tries):
        try:
            return query(pattern, lat, lng, radius_km)
        except Exception as e:  # noqa: BLE001
            if i == tries - 1:
                raise
            print(f"  다시 시도 {i + 1}/{tries - 1} ({e})")
            time.sleep(10 * (i + 1))


def query(pattern, lat, lng, radius_km):
    pat = pattern.replace("\\", "\\\\").replace('"', '\\"')
    q = f'[out:json][timeout:60];nwr["name"~"{pat}"](around:{int(radius_km * 1000)},{lat},{lng});out center tags 30;'
    req = urllib.request.Request(URL, data=urllib.parse.urlencode({"data": q}).encode(),
                                 headers={"User-Agent": "TabiMate-student-project/1.0"})
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode())["elements"]


def pick(elements, lat, lng):
    rows = []
    for e in elements:
        c = e.get("center") or {"lat": e.get("lat"), "lon": e.get("lon")}
        if c.get("lat") is None:
            continue
        t = e.get("tags", {})
        rows.append({"osmId": f"{e['type']}/{e['id']}", "name": t.get("name"), "lat": c["lat"], "lng": c["lon"],
                     "tags": {k: t[k] for k in GOOD_TAGS + ("amenity", "railway", "building") if k in t},
                     "km": round(km((lat, lng), (c["lat"], c["lon"])), 2)})
    # 관광 관련 태그가 있는 것 → 면/관계(큰 곳) → 도시 중심에 가까운 것 순
    rows.sort(key=lambda r: (not any(k in r["tags"] for k in GOOD_TAGS), not r["osmId"].startswith(("way", "rel")), r["km"]))
    return rows


def main():
    dst = ROOT / "data" / "extra" / "night_spots_osm.json"
    prev = {}
    if dst.exists():
        prev = {(o["city"], o["name"]): o for o in json.loads(dst.read_text(encoding="utf-8"))}
    out = []
    for line in (ROOT / "data" / "scripts" / "night_spots_osm.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        city, name, pattern = line.split("\t")[:3]
        old = prev.get((city, name))
        if old and old.get("pick") and old.get("pattern") == pattern:
            out.append(old)
            print(f"{city:12s} {name:24s} = {old['pick']['name']} {old['pick']['osmId']} (이전 결과)")
            continue
        lat, lng, radius = CITY_RADIUS[city]
        try:
            cands = pick(query_retry(pattern, lat, lng, radius), lat, lng)
        except Exception as e:  # noqa: BLE001 - 한 곳 실패해도 나머지는 계속
            print(f"  실패 {city} {name}: {e}")
            cands = []
        best = cands[0] if cands else None
        out.append({"city": city, "name": name, "pattern": pattern, "pick": best, "candidates": cands[:5]})
        print(f"{city:12s} {name:24s} → {best['name'] + ' ' + best['osmId'] if best else '못 찾음'}")
        time.sleep(3)  # Overpass 예의상 간격
    dst.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"저장: {dst} (찾음 {sum(1 for o in out if o['pick'])}/{len(out)})")


if __name__ == "__main__":
    main()
