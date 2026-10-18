"""노선 데이터 검사 (10-18).  프로젝트 루트에서: python data/scripts/rail_audit.py
출력: data/processed/rail_audit_report.txt, rail_audit_flags.tsv"""
import csv
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from data_clean import CITY_RADIUS, km  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
RAIL = ROOT / "data" / "osm_rail"
EXTRA = ROOT / "data" / "extra"
REGION = {"tokyo": ["tokyo", "yokohama", "kamakura"], "kansai": ["osaka", "kyoto", "kobe", "nara"],
          "sapporo": ["sapporo", "otaru"], "nagoya": ["nagoya"], "fukuoka": ["fukuoka"], "hiroshima": ["hiroshima"],
          "naha": ["naha"], "kanazawa": ["kanazawa"], "hakone": ["hakone"], "kawaguchiko": ["kawaguchiko"],
          "takayama": ["takayama"], "beppu": ["beppu"], "yufuin": ["yufuin"]}
JUMP_KM = {"subway": 6, "tram": 3, "light_rail": 6, "monorail": 6, "train": 40, "funicular": 3, "ferry": 999, "bus": 15}
out, flags = [], []


def P(*a):
    out.append(" ".join(str(x) for x in a))


def F(region, kind, name, detail=""):
    flags.append([region, kind, name, detail])


def n(s):
    return re.sub(r"[\s・()（）駅]", "", unicodedata.normalize("NFKC", s or ""))


def inside(region, lat, lng, slack=1.0):
    return any(km(CITY_RADIUS[c][:2], (lat, lng)) <= CITY_RADIUS[c][2] * slack for c in REGION[region])


def load(p, default):
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else default


mlit = load(EXTRA / "mlit_stations.json", [])
wheel = load(RAIL / "attractions_wheelchair.json", {})
attr = {a["osmId"] for a in load(ROOT / "data" / "processed" / "clean_attractions.json", [])}
P("휠체어 정보", len(wheel), "중 관광지와 연결", sum(1 for k in wheel if k in attr))

for region in REGION:
    routes = load(RAIL / f"{region}_routes.json", [])
    stops = load(RAIL / f"{region}_stops.json", {})
    stations = load(RAIL / f"{region}_stations.json", [])
    areas = load(RAIL / f"{region}_stop_areas.json", [])
    aerial = load(RAIL / f"{region}_aerial.json", {"lines": [], "stations": []})
    extra = load(RAIL / f"{region}_extra.json", {})
    P(f"\n==== {region}: 노선 {len(routes)} {dict(Counter(r['route'] for r in routes))}, 노선 안 역 {len(stops)}, 역 {len(stations)}, "
      f"갈아타기 묶음 {len(areas)}, 케이블 {len(aerial.get('lines', []))}, 부가 {({k: len(v) for k, v in extra.items()})}")
    ids = Counter(r["id"] for r in routes)
    for k, v in ids.items():
        if v > 1:
            F(region, "노선 id 중복", k, v)
    for r in routes:
        nm = r.get("nameJa") or r.get("name") or r.get("ref") or str(r["id"])
        miss = [s for s in r["stops"] if str(s) not in stops]
        if not r["stops"]:
            F(region, "역 없는 노선", nm, r["route"])
            continue
        if miss:
            F(region, "노선의 역 좌표 없음", nm, f"{len(miss)}/{len(r['stops'])}")
        pts = [stops[str(s)] for s in r["stops"] if str(s) in stops]
        if len(pts) < 2:
            F(region, "역 1개 이하 노선", nm, r["route"])
        lim = JUMP_KM.get(r["route"], 40)
        for a, b in zip(pts, pts[1:]):
            d = km((a["lat"], a["lng"]), (b["lat"], b["lng"]))
            if d > lim:
                F(region, "역 사이 거리 비정상", nm, f"{a.get('name')}→{b.get('name')} {d:.1f}km ({r['route']})")
                break
        if pts and not any(inside(region, p["lat"], p["lng"]) for p in pts):
            F(region, "도시권 안 역이 없는 노선", nm, r["route"])
        if not (r.get("name") or r.get("nameJa") or r.get("ref")):
            F(region, "이름 없는 노선", r["id"], r["route"])
    for sid, s in stops.items():
        if not s.get("name") and s.get("publicTransport") != "platform":
            F(region, "이름 없는 역(노선 안)", sid, f"{s['lat']:.4f},{s['lng']:.4f}")
    for s in stations:
        if not s.get("name"):
            F(region, "이름 없는 역", s["id"], f"{s['lat']:.4f},{s['lng']:.4f}")
    names = defaultdict(list)
    for s in stations:
        names[n(s.get("name"))].append(s)
    # 국토수치정보 역이 OSM 역에 있는지 (도시권 안, 이름 같고 1km 안)
    ml = [m for m in mlit if inside(region, m["lat"], m["lng"])]
    lost = [m for m in ml if not any(km((m["lat"], m["lng"]), (s["lat"], s["lng"])) < 1.0 for s in names.get(n(m["name"]), []))]
    P(f"  국토수치정보 역 {len(ml)} 중 OSM 역에 없음 {len(lost)}")
    for m in lost:
        F(region, "국토수치정보에는 있는데 OSM 역 없음", m["name"], f"{m['line']} / {m['operator']}")
    for a in areas:
        if not a.get("members"):
            F(region, "빈 갈아타기 묶음", a.get("name"), a["id"])

# 버스
for f in sorted(EXTRA.glob("bus_*_routes.json")) + [RAIL / "kyoto_bus_routes.json"]:
    city = f.name.replace("bus_", "").replace("_routes.json", "")
    region = next((r for r, cs in REGION.items() if city in cs), city)
    routes = load(f, [])
    stops = load(f.with_name(f.name.replace("routes", "stops")), {})
    far = 0
    for r in routes:
        pts = [stops[str(s)] for s in r["stops"] if str(s) in stops]
        ins = [p for p in pts if inside(region, p["lat"], p["lng"])]
        if len(pts) <= 1 or len(ins) < len(pts) / 2:
            far += 1
            F(f"bus_{city}", "도시 밖 노선(장거리·역 1개)", r.get("name") or r.get("ref"), f"역 {len(pts)} 중 도시권 {len(ins)}")
    P(f"\n==== 버스 {city}: 노선 {len(routes)}, 정류장 {len(stops)}, 도시 밖·역 1개 노선 {far}")

P("\n==== 문제 요약")
for (rg, k), c in sorted(Counter((x[0], x[1]) for x in flags).items()):
    P(f"{rg:16s} {k:34s} {c}")
(ROOT / "data" / "processed" / "rail_audit_report.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
with open(ROOT / "data" / "processed" / "rail_audit_flags.tsv", "w", encoding="utf-8", newline="") as fp:
    w = csv.writer(fp, delimiter="\t")
    w.writerow(["region", "kind", "name", "detail"])
    w.writerows(flags)
print("\n".join(out))
