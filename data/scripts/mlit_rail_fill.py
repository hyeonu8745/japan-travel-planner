"""OSM 에 노선(역 순서)이 없는 철도를 국토수치정보 N02(2025)로 채운다 (10-18). 인터넷 없이 파일만 읽는다.

    프로젝트 루트에서:  python data/scripts/mlit_rail_fill.py

입력  data/extra/mlit/N02-25_GML/UTF-8/N02-25_Station.geojson   (역: 노선명·회사·역명·역 선형)
      data/osm_rail/<지역>_routes.json, <지역>_stops.json, <지역>_stations.json
출력  data/osm_rail/<지역>_routes_mlit.json   OSM 노선과 같은 형식 + "source": "mlit", "bidirectional": true
      data/osm_rail/<지역>_stops_mlit.json    {"m<역코드>": {name, lat, lng, osmStation(같은 역 OSM id 또는 null), ...}}
      data/processed/mlit_rail_fill_report.txt

채우는 노선: 지역 도시권 안에 역이 2개 이상이고, 그중 2개 이상이 OSM 노선 위에 없는 (회사, 노선). 겹치는 구간은 OSM 노선과 함께 쓴다.
역 순서: 국토수치정보에는 순서가 없어서, 역 위치로 가장 짧은 한 줄 경로를 만든다
        (가장 먼 두 역 중 한쪽에서 가까운 역 잇기 → 2-opt 로 꼬임 풀기). 갈래가 없는 노선 기준.
"""
import json
import math
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from data_clean import CITY_RADIUS, km  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
RAIL = ROOT / "data" / "osm_rail"
N02 = ROOT / "data" / "extra" / "mlit" / "N02-25_GML" / "UTF-8" / "N02-25_Station.geojson"
REGION = {"tokyo": ["tokyo", "yokohama", "kamakura"], "kansai": ["osaka", "kyoto", "kobe", "nara"],
          "sapporo": ["sapporo", "otaru"], "nagoya": ["nagoya"], "fukuoka": ["fukuoka"], "hiroshima": ["hiroshima"],
          "naha": ["naha"], "kanazawa": ["kanazawa"], "hakone": ["hakone"], "kawaguchiko": ["kawaguchiko"],
          "takayama": ["takayama"], "beppu": ["beppu"], "yufuin": ["yufuin"]}
SLACK = 1.3          # 도시권 반경의 1.3배 안 역이 기준 (오타루처럼 경계 근처 역을 잇기 위해)
ORDER_SLACK = 2.0    # 순서는 더 넓게(2배) 잡아 만든 뒤, 기준 역의 처음~끝 구간만 남긴다 (경계에 걸린 중간 역이 빠지지 않게)
MIN_MISSING = 2      # OSM 노선 위에 없는 역이 이만큼 이상이면 채운다 (히로덴처럼 일부만 빠진 노선 포함)
SAME_STATION_KM = 0.6
# N02_001 철도 구분 → OSM route 값
KIND = {"11": "train", "12": "train", "13": "funicular", "14": "monorail", "15": "light_rail", "16": "bus", "17": "tram",
        "21": "train", "22": "train", "23": "monorail", "24": "light_rail", "25": "tram"}


def n(s):
    return re.sub(r"[\s・()（）〈〉<>駅]", "", unicodedata.normalize("NFKC", s or ""))


def inside(region, lat, lng, slack=SLACK):
    return any(km(CITY_RADIUS[c][:2], (lat, lng)) <= CITY_RADIUS[c][2] * slack for c in REGION[region])


def order_stations(pts):
    """좌표 목록 → 한 줄로 잇는 순서(인덱스). 가장 먼 쌍의 한쪽에서 시작해 가까운 순으로 잇고 2-opt 로 다듬는다."""
    m = len(pts)
    if m <= 2:
        return list(range(m))
    d = [[km(pts[i], pts[j]) for j in range(m)] for i in range(m)]
    a, _ = max(((i, j) for i in range(m) for j in range(i + 1, m)), key=lambda p: d[p[0]][p[1]])
    order, left = [a], set(range(m)) - {a}
    while left:
        nxt = min(left, key=lambda j: d[order[-1]][j])
        order.append(nxt)
        left.remove(nxt)
    improved = True
    while improved:   # 열린 경로 2-opt: order[i..j] 를 뒤집어 짧아지면 바꾼다
        improved = False
        for i in range(1, m):
            for j in range(i + 1, m):
                before = d[order[i - 1]][order[i]] + (d[order[j]][order[j + 1]] if j + 1 < m else 0)
                after = d[order[i - 1]][order[j]] + (d[order[i]][order[j + 1]] if j + 1 < m else 0)
                if after + 1e-9 < before:
                    order[i:j + 1] = order[i:j + 1][::-1]
                    improved = True
    return order


def trim_span(seq, keep):
    """순서대로 놓인 역 중 keep(역) 이 참인 첫 역~마지막 역 구간만."""
    idx = [i for i, s in enumerate(seq) if keep(s)]
    return seq[idx[0]:idx[-1] + 1] if idx else []


def load_mlit():
    feats = json.loads(N02.read_text(encoding="utf-8"))["features"]
    lines = defaultdict(dict)
    for f in feats:
        p = f["properties"]
        c = f["geometry"]["coordinates"]
        lat, lng = sum(x[1] for x in c) / len(c), sum(x[0] for x in c) / len(c)
        key = (p["N02_004"], p["N02_003"])
        name = p["N02_005"]
        if name not in lines[key]:   # 같은 노선에 같은 역이 두 번(승강장 조각) 있으면 하나로
            lines[key][name] = {"name": name, "lat": lat, "lng": lng, "code": p["N02_005c"], "kind": KIND.get(p["N02_001"], "train")}
    return lines


def served_names(region):
    routes = json.loads((RAIL / f"{region}_routes.json").read_text(encoding="utf-8"))
    stops = json.loads((RAIL / f"{region}_stops.json").read_text(encoding="utf-8"))
    served = defaultdict(list)
    for r in routes:
        for s in r["stops"] + r.get("platforms", []):
            p = stops.get(str(s))
            if p and p.get("name"):
                served[n(p["name"])].append((p["lat"], p["lng"]))
    return served


def is_served(served, st):
    return any(km(q, (st["lat"], st["lng"])) < 1.0 for q in served.get(n(st["name"]), []))


def fill_region(region, mlit):
    served = served_names(region)
    stations = json.loads((RAIL / f"{region}_stations.json").read_text(encoding="utf-8"))
    by_name = defaultdict(list)
    for s in stations:
        by_name[n(s.get("name"))].append(s)
    routes, stops, report = [], {}, []
    for (company, line), sts in sorted(mlit.items()):
        ins = [s for s in sts.values() if inside(region, s["lat"], s["lng"])]
        if len(ins) < 2:
            continue
        missing = [s for s in ins if not is_served(served, s)]
        if len(missing) < MIN_MISSING:
            continue
        wide = [s for s in sts.values() if inside(region, s["lat"], s["lng"], ORDER_SLACK)]
        seq = [wide[i] for i in order_stations([(s["lat"], s["lng"]) for s in wide])]
        seq = trim_span(seq, lambda s: inside(region, s["lat"], s["lng"]))
        ids = []
        for s in seq:
            sid = f"m{s['code']}"
            osm = next((o["id"] for o in by_name.get(n(s["name"]), [])
                        if km((o["lat"], o["lng"]), (s["lat"], s["lng"])) < SAME_STATION_KM), None)
            stops[sid] = {"name": s["name"], "nameJa": s["name"], "nameEn": None, "nameKo": None,
                          "lat": round(s["lat"], 6), "lng": round(s["lng"], 6), "operator": company,
                          "railway": "stop", "publicTransport": "stop_position", "wheelchair": None, "osmStation": osm}
            ids.append(sid)
        gaps = [km((a["lat"], a["lng"]), (b["lat"], b["lng"])) for a, b in zip(seq, seq[1:])]
        routes.append({"id": f"mlit:{company}:{line}", "route": seq[0]["kind"], "ref": None, "name": line,
                       "nameJa": f"{line} ({seq[0]['name']}~{seq[-1]['name']})", "nameEn": None, "nameKo": None,
                       "colour": None, "operator": company, "network": None, "from": seq[0]["name"], "to": seq[-1]["name"],
                       "master": None, "timestamp": None, "stops": ids, "platforms": [],
                       "source": "mlit", "bidirectional": True})
        report.append(f"  {company} {line}: 역 {len(seq)} (OSM 노선에 없던 역 {len(missing)}/{len(ins)}), 최대 역간 {max(gaps):.1f}km\n"
                      f"    {' → '.join(s['name'] for s in seq)}")
    return routes, stops, report


def main():
    mlit = load_mlit()
    lines = [f"국토수치정보 노선 {len(mlit)}개"]
    for region in REGION:
        if not (RAIL / f"{region}_routes.json").exists():
            continue
        routes, stops, rep = fill_region(region, mlit)
        (RAIL / f"{region}_routes_mlit.json").write_text(json.dumps(routes, ensure_ascii=False, indent=1), encoding="utf-8")
        (RAIL / f"{region}_stops_mlit.json").write_text(json.dumps(stops, ensure_ascii=False, indent=1), encoding="utf-8")
        lines.append(f"\n[{region}] 채운 노선 {len(routes)}, 역 {len(stops)}")
        lines += rep
    text = "\n".join(lines)
    (ROOT / "data" / "processed" / "mlit_rail_fill_report.txt").write_text(text + "\n", encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
