"""
수집한 데이터를 11월 가져오기(DB 적재) 전에 정리한다. 인터넷 없이 파일만 읽는다.

    프로젝트 루트에서:  python data/scripts/data_clean.py

입력  data/processed/tabelog_geocoded.json, osm_attractions.json, osm_chains.json
      data/scripts/brand_dictionary.tsv, sensitive_places.tsv, excluded_places.tsv
출력  data/processed/clean_restaurants_tabelog.json  타베로그 (좌표 있는 곳만, 휴일·예산·표시 정리)
      data/processed/clean_restaurants_chain.json    체인점 (사전에 있는 브랜드만, 종류·장르·예산 붙임)
      data/processed/clean_attractions.json          관광지 (중복 합침, 민감 장소 제외/표시, 한국어 이름 정리)
      data/processed/clean_facilities.json           편의시설 (data/extra/facility_*.json 중 도시권 안만, city·type 붙임)
      data/processed/clean_report.txt                요약
"""
import csv
import json
import math
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROC = ROOT / "data" / "processed"
SCRIPTS = ROOT / "data" / "scripts"
WEEK = "月火水木金土日"
WEEK_KO = dict(zip(WEEK, ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]))


def norm(s):
    return unicodedata.normalize("NFKC", s or "").strip()


def km(a, b):
    lat1, lng1, lat2, lng2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lng2 - lng1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(h))


# ---------- 타베로그 ----------

def budget(raw):
    """서버 BudgetParser 와 같은 규칙 + 「～￥999」(하한 없음)는 하한 None 으로 둔다(서버는 0 → 식비 요약에서 보정)"""
    s = norm(raw)
    if not s or s == "-":
        return None, None
    nums = [int(n.replace(",", "")) for n in re.findall(r"\d[\d,]*", s)]
    if not nums:
        return None, None
    if "~" not in s:
        return nums[0], nums[0]
    left, right = s.split("~", 1)
    lo = re.findall(r"\d[\d,]*", left)
    hi = re.findall(r"\d[\d,]*", right)
    return (int(lo[0].replace(",", "")) if lo else None), (int(hi[0].replace(",", "")) if hi else None)


def closed_days(hours, holiday=None):
    """「月・火 定休日」「日 定休日」(Apify 영업시간) 또는 「日曜日、祝日」(캐글 holiday) → ['MON','TUE'] / 「不定休」 → irregular"""
    h = norm(hours)
    days = set()
    for m in re.finditer(r"([月火水木金土日](?:・[月火水木金土日])*)\s*定休日", h):
        days.update(WEEK_KO[d] for d in m.group(1).split("・"))
    hol = norm(holiday)
    if hol and not re.search(r"第\d|隔週|不定", hol):   # 「第2月曜」처럼 매주가 아닌 건 요일로 넣지 않는다
        days.update(WEEK_KO[d] for d in re.findall(r"([月火水木金土日])曜", hol))
    irregular = "不定休" in h or "不定休" in hol
    return sorted(days, key=lambda d: list(WEEK_KO.values()).index(d)), irregular


def station_distance(text):
    m = re.search(r"から(\d+)m\s*$", norm(text))
    return int(m.group(1)) if m else None


# 도시 중심과 반경(km). 이보다 먼 식당은 여행 동선 밖이라 뺀다 (10-18 데이터 검사: 오쿠히다·오이타 시내·제니바코 등)
CITY_RADIUS = {
    "tokyo": (35.681, 139.767, 25), "osaka": (34.69, 135.50, 15), "kyoto": (35.01, 135.77, 15),
    "kobe": (34.69, 135.19, 15), "nara": (34.685, 135.83, 12), "yokohama": (35.45, 139.63, 15),
    "fukuoka": (33.59, 130.40, 15), "sapporo": (43.06, 141.35, 15), "nagoya": (35.17, 136.91, 15),
    "hiroshima": (34.39, 132.46, 20), "naha": (26.21, 127.68, 12), "kanazawa": (36.56, 136.65, 15),
    "hakone": (35.23, 139.05, 15), "kamakura": (35.32, 139.55, 8), "takayama": (36.14, 137.25, 10),
    "otaru": (43.19, 141.0, 10), "yufuin": (33.265, 131.36, 8), "beppu": (33.28, 131.49, 10),
    "kawaguchiko": (35.50, 138.76, 15),
}
# 타베로그 지역 묶음에 섞여 들어온 다른 동네 (하코네 = 「箱根・湯河原・真鶴」, 오다와라는 하코네 입구라 남김 - 10-18 사용자 결정)
OTHER_TOWNS = {"hakone": ("湯河原町", "真鶴町")}
MOVED_NOTE = re.compile(r"\s*このお店は.*$", re.S)


def in_city(city, lat, lng):
    c = CITY_RADIUS.get(city)
    return c is None or km((c[0], c[1]), (lat, lng)) <= c[2]


def clean_address(addr):
    """주소 뒤에 붙은 「このお店は「…」から移転しています。※移転前の情報は…」 안내문을 잘라 낸다."""
    a = MOVED_NOTE.sub("", addr or "").strip()
    return a or None


def clean_tabelog():
    src = json.loads((PROC / "tabelog_geocoded.json").read_text(encoding="utf-8"))
    out, dropped = [], Counter()
    for s in src:
        if s.get("lat") is None:
            dropped["좌표 없음"] += 1
            continue
        city = s["areas"][0].split("_")[0]
        address = clean_address(s.get("address"))
        if any(t in (address or "") for t in OTHER_TOWNS.get(city, ())):
            dropped["다른 동네(유가와라·마나즈루)"] += 1
            continue
        if not in_city(city, s["lat"], s["lng"]):
            dropped["도시권 밖"] += 1
            continue
        genres = [g for g in norm(s.get("restaurantGenre")).split("、") if g]
        lmin, lmax = budget(s.get("budgetLunch"))
        dmin, dmax = budget(s.get("budgetDinner"))
        days, irregular = closed_days(s.get("businessHours"), s.get("holiday"))
        awards = s.get("awards") or []
        out.append({
            "storeId": s["storeId"], "name": s["restaurantName"], "url": s["restaurantUrl"],
            "areas": s["areas"], "city": s["areas"][0].split("_")[0],
            "lat": s["lat"], "lng": s["lng"], "geoLevel": s["geoLevel"], "suspect": s.get("suspect", False),
            "address": address, "rating": s.get("restaurantRating"), "reviewCount": s.get("totalReviewCount"),
            "genres": genres,
            "budgetLunch": s.get("budgetLunch"), "budgetLunchMin": lmin, "budgetLunchMax": lmax,
            "budgetDinner": s.get("budgetDinner"), "budgetDinnerMin": dmin, "budgetDinnerMax": dmax,
            "businessHours": s.get("businessHours"), "holiday": s.get("holiday"), "source": s.get("source", "apify"), "closedDays": days, "closedIrregular": irregular,
            "nearestStation": s.get("nearestStation"), "nearestDistance": station_distance(s.get("nearestStation")),
            "reservationOnly": norm(s.get("reservation")).startswith("完全予約制"),
            "smokingAllSeats": "全席喫煙可" in norm(s.get("smokingPolicy")),
            "koreanMenu": "韓国語" in norm(s.get("services")),
            "hyakumeiten": any("百名店" in a for a in awards),
            "awardMedal": next((m for m in ("Gold", "Silver", "Bronze") if any(m in a for a in awards)), None),
            "standingBar": any("立ち飲み" in g for g in genres),
        })
    return out, dropped


# ---------- 편의시설 ----------

def city_of(lat, lng):
    """반경 안에 드는 도시 중 (거리 / 반경)이 가장 작은 곳. 어디에도 안 들면 None."""
    best, best_r = None, None
    for city, (clat, clng, radius) in CITY_RADIUS.items():
        r = km((clat, clng), (lat, lng)) / radius
        if r <= 1 and (best_r is None or r < best_r):
            best, best_r = city, r
    return best


def clean_facilities():
    """data/extra/facility_*.json (종류별 목록) → 도시권 안의 점만, city·type 붙인 한 줄 목록. 도쿄 파일의 요코하마·가마쿠라 점은 그 도시로."""
    out, dropped, seen = [], Counter(), set()
    for f in sorted((ROOT / "data" / "extra").glob("facility_*.json")):
        for typ, rows in json.loads(f.read_text(encoding="utf-8")).items():
            for x in rows:
                if x.get("lat") is None or x.get("lng") is None:
                    dropped["좌표 없음"] += 1
                    continue
                if x.get("osmId") in seen:
                    dropped["중복"] += 1
                    continue
                city = city_of(x["lat"], x["lng"])
                if city is None:
                    dropped["도시권 밖"] += 1
                    continue
                seen.add(x.get("osmId"))
                out.append({**x, "type": typ, "city": city})
    return out, dropped


# ---------- 체인점 ----------

def load_brands():
    brands = {}
    for line in (SCRIPTS / "brand_dictionary.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        c = (line.split("\t") + [""] * 7)[:7]
        info = {"brandKo": c[1], "category": c[2], "genreKo": c[3],
                "budgetMin": int(c[4]) if c[4] else None, "budgetMax": int(c[5]) if c[5] else None, "note": c[6]}
        for alias in c[0].split("|"):
            brands[norm(alias)] = dict(info, brand=c[0].split("|")[0])
    return brands


def clean_chains():
    brands = load_brands()
    src = json.loads((PROC / "osm_chains.json").read_text(encoding="utf-8"))
    out, skipped = [], Counter()
    for c in src:
        b = brands.get(norm(c.get("brand")))
        if not b:
            skipped["사전에 없는 브랜드"] += 1
            continue
        if b["category"] in ("TAKEOUT", "EXCLUDE"):
            skipped[b["category"]] += 1
            continue
        out.append({
            "osmId": c["osmId"], "name": c.get("name") or c["brand"], "brand": b["brand"], "brandKo": b["brandKo"],
            "category": b["category"], "genreKo": b["genreKo"], "budgetMin": b["budgetMin"], "budgetMax": b["budgetMax"],
            "lat": c["lat"], "lng": c["lng"], "areas": c["areas"], "city": c["areas"][0].split("_")[0],
            "openingHours": c.get("openingHours"),
        })
    return out, skipped


# ---------- 관광지 ----------

RULE_FILES = ("sensitive_places.tsv", "excluded_places.tsv")  # 민감 장소 / 폐관·관람 불가·관광지 아님 (10-18)
SAME_NAME_KM = 0.5  # 이름이 같으면 이 거리 안은 같은 곳 (10-18: 200m → 500m, 상점가·공원이 두 번 들어감)


def parse_rules(text):
    rules = []
    for line in text.splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        c = (line.split("\t") + [""] * 4)[:4]
        rules.append({"action": c[0], "pattern": re.compile(c[1]) if c[1] else None, "wikidata": c[2], "reason": c[3]})
    return rules


def load_sensitive():
    rules = []
    for name in RULE_FILES:
        f = SCRIPTS / name
        if f.exists():
            rules += parse_rules(f.read_text(encoding="utf-8"))
    return rules


def sensitive_of(a, rules):
    names = " ".join(filter(None, [a.get("name"), a.get("nameJa"), a.get("nameEn")]))
    for r in rules:
        if (r["wikidata"] and a.get("wikidata") == r["wikidata"]) or (r["pattern"] and r["pattern"].search(names)):
            return r
    return None


def same_name_key(name):
    return re.sub(r"\s", "", norm(name))


def same_place(merged, a):
    """이름(공백 무시)이 같고 SAME_NAME_KM 안에 있는 앞선 항목. 둘 다 위키데이터(장소)가 있는데 다르면 다른 곳."""
    def wd(x):
        return x.get("wikidata") if x.get("wikidataIsPlace") else None
    return next((m for m in merged if same_name_key(m["name"]) == same_name_key(a["name"])
                 and not (wd(m) and wd(a) and wd(m) != wd(a))
                 and km((m["lat"], m["lng"]), (a["lat"], a["lng"])) < SAME_NAME_KM), None)


def clean_attractions():
    rules = load_sensitive()
    src = json.loads((PROC / "osm_attractions.json").read_text(encoding="utf-8"))
    excluded, noted = [], 0
    # 1) 같은 위키데이터는 하나로 (노드·면 두 번 들어간 고쿄·나고야성 등). 좌표는 첫 번째, 지역은 합침
    by_key = {}
    for a in src:
        key = a.get("wikidata") if a.get("wikidataIsPlace") else None
        key = key or f"{norm(a['name'])}@{round(a['lat'], 3)},{round(a['lng'], 3)}"
        if key in by_key:
            keep = by_key[key]
            keep["areas"] = sorted(set(keep["areas"]) | set(a["areas"]))
            keep["nameKo"] = keep.get("nameKo") or a.get("nameKo")
            continue
        by_key[key] = dict(a)
    # 2) 이름이 같고 200m 안이면 하나로 (위키데이터가 한쪽에만 붙은 경우)
    merged = []
    for a in sorted(by_key.values(), key=lambda x: -x.get("sitelinks", 0)):
        dup = same_place(merged, a)
        if dup:
            dup["areas"] = sorted(set(dup["areas"]) | set(a["areas"]))
            continue
        merged.append(a)
    out = []
    for a in merged:
        r = sensitive_of(a, rules)
        if r and r["action"] == "EXCLUDE":
            excluded.append(f"{a['name']} ({a['areas'][0]}) - {r['reason']}")
            continue
        ko = (a.get("nameKo") or "").split(";")[0].strip() or None
        out.append({
            "osmId": a["osmId"], "name": a["name"], "nameJa": a.get("nameJa"), "nameEn": a.get("nameEn"), "nameKo": ko,
            "kind": a["kind"], "lat": a["lat"], "lng": a["lng"], "areas": a["areas"], "city": a["areas"][0].split("_")[0],
            "wikidata": a.get("wikidata") if a.get("wikidataIsPlace") else None,
            "popularity": a.get("sitelinks", 0), "openingHours": a.get("openingHours"),
            "website": (a.get("tags") or {}).get("website"), "fee": (a.get("tags") or {}).get("fee"),
            "historyNote": r["reason"] if r and r["action"] == "NOTE" else None,
        })
        noted += bool(r)
    return out, excluded, noted, len(src)


def main():
    tabelog, t_drop = clean_tabelog()
    chains, c_skip = clean_chains()
    attractions, excluded, noted, a_src = clean_attractions()
    facilities, f_drop = clean_facilities()
    for name, data in (("clean_restaurants_tabelog", tabelog), ("clean_restaurants_chain", chains),
                       ("clean_attractions", attractions), ("clean_facilities", facilities)):
        (PROC / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")

    lines = [
        f"[타베로그] {len(tabelog)}곳 (제외 {dict(t_drop)})",
        f"  도시별 {dict(Counter(t['city'] for t in tabelog).most_common())}",
        f"  3.5 이상 {sum(1 for t in tabelog if (t['rating'] or 0) >= 3.5)}, 백명점 {sum(t['hyakumeiten'] for t in tabelog)}, "
        f"완전 예약제 {sum(t['reservationOnly'] for t in tabelog)}, 전석 흡연 {sum(t['smokingAllSeats'] for t in tabelog)}, "
        f"한국어 메뉴 {sum(t['koreanMenu'] for t in tabelog)}, 다치노미 {sum(t['standingBar'] for t in tabelog)}",
        f"  정기휴일 요일 있음 {sum(1 for t in tabelog if t['closedDays'])}, 부정기 {sum(t['closedIrregular'] for t in tabelog)}, "
        f"점심 예산 있음 {sum(1 for t in tabelog if t['budgetLunchMax'])}, 저녁 1만엔 이하 {sum(1 for t in tabelog if t['budgetDinnerMax'] and t['budgetDinnerMax'] <= 9999)}",
        f"[체인점] {len(chains)}곳 (건너뜀 {dict(c_skip)})",
        f"  종류 {dict(Counter(c['category'] for c in chains))}, 도시별 {dict(Counter(c['city'] for c in chains).most_common())}",
        f"[관광지] 원본 {a_src} → 중복 합친 뒤 {len(attractions) + len(excluded)} → 제외(민감·폐관·관람 불가) {len(excluded)} → {len(attractions)}곳 (역사 안내 표시 {noted})",
        f"  종류 {dict(Counter(a['kind'] for a in attractions).most_common())}",
        f"  인기도 있음(위키데이터 장소) {sum(1 for a in attractions if a['popularity'])}, 한국어 이름 {sum(1 for a in attractions if a['nameKo'])}",
        f"[편의시설] {len(facilities)}곳 (제외 {dict(f_drop)})",
        f"  종류 {dict(Counter(x['type'] for x in facilities).most_common())}",
        f"  도시별 {dict(Counter(x['city'] for x in facilities).most_common())}",
        "  제외된 곳:", *[f"    - {e}" for e in excluded],
    ]
    report = "\n".join(lines)
    (PROC / "clean_report.txt").write_text(report, encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
