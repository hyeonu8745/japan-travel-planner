"""유명 관광지 보충 수집 (10-19). 인터넷이 되는 PC에서 한 번 실행 (10~20분).

    프로젝트 루트에서:  python data/scripts/osm_popular_collect.py

처음 관광지 수집(osm_collect.py)은 식당이 모인 지역 중심 반경 0.8~3km 만 받아서
금각사·은각사·헤이안 신궁·다자이후·이쓰쿠시마·가이유칸 같은 번화가 밖 대표 관광지가 빠졌다.
1) 도시 중심 반경(data_clean.CITY_RADIUS) 안에서 위키데이터가 붙은 관광지·절·신사·성·공원·시장 → 위키 링크 수(인기도)
2) data/scripts/must_see.tsv(꼭 가는 관광지 목록) 중 지금 관광지 데이터에 없는 곳을 이름으로 찾기 (시장·거리 등)
출력  data/extra/osm_popular.json  → data_clean.py 가 합친다 → extra_collect.py wiki 로 새 관광지 사진
"""
import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from data_clean import CITY_RADIUS  # noqa: E402
from osm_collect import kind_of  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DST = ROOT / "data" / "extra" / "osm_popular.json"
MUST = ROOT / "data" / "scripts" / "must_see.tsv"
ATTR = ROOT / "data" / "processed" / "clean_attractions.json"
OVERPASS = "https://overpass-api.de/api/interpreter"
WIKIDATA = "https://www.wikidata.org/w/api.php"
UA = "TabiMate-student-project/1.0 (osm popular attractions)"
KEEP_TAGS = ("tourism", "historic", "amenity", "religion", "leisure", "shop", "highway", "place", "website", "opening_hours", "fee")


def key(s):
    return re.sub(r"[\s・･()（）「」]", "", unicodedata.normalize("NFKC", s or "")).lower()


def overpass(q, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(OVERPASS, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=300) as r:
                return json.loads(r.read().decode())["elements"]
        except Exception as e:  # noqa: BLE001
            if i == tries - 1:
                raise
            print(f"  다시 시도 {i + 1}/{tries - 1} ({e})")
            time.sleep(20 * (i + 1))


def row_of(e, city):
    t = e.get("tags", {})
    c = e.get("center") or {"lat": e.get("lat"), "lon": e.get("lon")}
    if not t.get("name") or c.get("lat") is None:
        return None
    return {"osmId": f"{e['type']}/{e['id']}", "city": city, "name": t["name"], "nameJa": t.get("name:ja"),
            "nameEn": t.get("name:en"), "nameKo": t.get("name:ko"), "lat": c["lat"], "lng": c["lon"],
            "wikidata": t.get("wikidata"), "kind": kind_of(t), "tags": {k: t[k] for k in KEEP_TAGS if k in t}}


def sweep(city, lat, lng, r):
    a = f"(around:{int(r * 1000)},{lat},{lng})"
    q = f"""[out:json][timeout:240][maxsize:268435456];
(
  nwr["wikidata"]["tourism"~"^(attraction|museum|viewpoint|theme_park|zoo|aquarium|gallery)$"]{a};
  nwr["wikidata"]["historic"~"^(castle|monument|memorial|ruins|archaeological_site|building|city_gate|temple|shrine)$"]{a};
  nwr["wikidata"]["amenity"~"^(place_of_worship|marketplace)$"]{a};
  nwr["wikidata"]["leisure"~"^(park|garden)$"]{a};
);
out center tags;"""
    return [x for x in (row_of(e, city) for e in overpass(q)) if x]


def score(r):
    """이름으로 찾은 후보 중 고르기: 위키데이터 → 관광 태그 → 면·관계(큰 곳)"""
    t = r["tags"]
    return (bool(r.get("wikidata")), any(k in t for k in ("tourism", "historic")) or t.get("amenity") in ("place_of_worship", "marketplace"),
            r["osmId"].startswith(("relation", "way")))


def must_see_missing():
    attrs = json.loads(ATTR.read_text(encoding="utf-8")) if ATTR.exists() else []
    out = []
    for line in MUST.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        city, pats, ko = (line.split("\t") + ["", ""])[:3]
        ps = [key(p) for p in pats.split("|")]
        if not any(a["city"] == city and any(p in key(a["name"]) + "|" + key(a.get("nameJa")) + "|" + key(a.get("nameEn")) for p in ps)
                   for a in attrs):
            out.append((city, pats, ko))
    return out


def main():
    data = {"sweep": [], "mustSee": []}
    for n, (city, (lat, lng, r)) in enumerate(CITY_RADIUS.items(), 1):
        try:
            rows = sweep(city, lat, lng, r)
        except Exception as e:  # noqa: BLE001
            print(f"[{n}/{len(CITY_RADIUS)}] {city} 실패: {e}")
            continue
        data["sweep"] += rows
        print(f"[{n}/{len(CITY_RADIUS)}] {city}: 위키데이터 있는 관광지 {len(rows)}")
        time.sleep(8)
    missing = must_see_missing()
    print(f"꼭 가는 관광지 중 데이터에 없는 곳 {len(missing)} → 이름으로 찾기")
    for city, pats, ko in missing:
        lat, lng, r = CITY_RADIUS[city]
        pat = "|".join(re.escape(p) for p in pats.split("|")).replace('"', '\\"')
        q = f'[out:json][timeout:120];nwr["name"~"{pat}"](around:{int(r * 1000)},{lat},{lng});out center tags 50;'
        try:
            cands = [x for x in (row_of(e, city) for e in overpass(q)) if x]
        except Exception as e:  # noqa: BLE001
            print(f"  {city} {ko} 실패: {e}")
            cands = []
        best = max(cands, key=score) if cands else None
        data["mustSee"].append({"city": city, "pattern": pats, "ko": ko, "pick": best, "candidates": len(cands)})
        print(f"  {city:11s} {ko:20s} → {best['name'] + ' ' + best['osmId'] if best else '못 찾음'}")
        time.sleep(3)
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
    DST.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"저장: {DST} (위키데이터 관광지 {len(data['sweep'])}, 위키 링크 8 이상 {sum(1 for x in data['sweep'] if x['sitelinks'] >= 8)}, "
          f"이름으로 찾음 {sum(1 for m in data['mustSee'] if m['pick'])}/{len(data['mustSee'])})")


if __name__ == "__main__":
    main()
