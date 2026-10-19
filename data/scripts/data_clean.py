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


WEEKLY_SKIP = re.compile(r"第\s*\d|毎月|隔週|月に|その他")
NO_HOLIDAY = re.compile(r"年中無休|無休|定休日\s*(なし|無し|無(?![\w]))|^\s*(なし|無し|無|ナシ)\s*$")
PAREN = re.compile(r"[（(][^）)]*[）)]")
DAY = re.compile(r"(?<![祝末始第元曜])([月火水木金土日])(?=曜|・|、|,|，|/|\s|$|祝)")


def _days_in(seg):
    """「月曜日(祝日の場合は翌日)」「火・水曜」「日・祝」「毎週水曜日」 → 요일들. 괄호 안·「第2火曜」 같은 매주가 아닌 표현은 빼고."""
    seg = PAREN.sub("", seg)
    days = set()
    for part in re.split(r"[、,，/／]", seg):   # 「日曜日、祝日、第1・3月曜」 → 第… 부분만 뺀다
        if not WEEKLY_SKIP.search(part):
            days |= {WEEK_KO[d] for d in DAY.findall(part)}
    return days, "祝" in seg


def closed_info(hours, holiday=None):
    """영업시간(Apify)·휴일(캐글) 글 → 정기휴일 요일, 부정기, 정기휴일 없음(年中無休), 공휴일 휴무.
    10-18: 「■ 定休日月曜日」「定休日毎週水曜日」(定休日 뒤에 요일), 캐글 「日・祝」「月」(曜 없이), 「無休」 처리 추가."""
    h, hol = norm(hours), norm(holiday)
    days, on_holiday = set(), False
    for m in re.finditer(r"([月火水木金土日](?:・[月火水木金土日])*)\s*定休日", h):   # 「水・日 定休日」
        days.update(WEEK_KO[d] for d in m.group(1).split("・"))
    for m in re.finditer(r"定休日\s*([^■【]{0,40})", h):                            # 「■ 定休日月曜日(祝日の場合は翌日)」
        d, ph = _days_in(m.group(1))
        days |= d
        on_holiday |= ph
    if hol:
        d, ph = _days_in(hol)
        days |= d
        on_holiday |= ph
    irregular = "不定" in h or "不定" in hol   # 不定休·不定期·不定
    texts = [m.group(0) for m in re.finditer(r"定休日\s*[^■【]{0,20}", h)] + ([hol] if hol else [])
    no_holiday = not days and any(NO_HOLIDAY.search(t) for t in texts)
    order = list(WEEK_KO.values())
    return {"closedDays": sorted(days, key=order.index), "closedIrregular": irregular,
            "noRegularHoliday": no_holiday, "closedOnHolidays": on_holiday}


def closed_days(hours, holiday=None):
    """(이전 호환) → (요일 목록, 부정기)"""
    c = closed_info(hours, holiday)
    return c["closedDays"], c["closedIrregular"]


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
        ci = closed_info(s.get("businessHours"), s.get("holiday"))
        days, irregular = ci["closedDays"], ci["closedIrregular"]
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
            "noRegularHoliday": ci["noRegularHoliday"], "closedOnHolidays": ci["closedOnHolidays"],
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
    """data/extra/facility_*.json + data/osm_rail/*_extra.json (종류별 목록) → 도시권 안의 점만, city·type 붙인 한 줄 목록.
    도쿄 파일의 요코하마·가마쿠라 점은 그 도시로."""
    out, dropped, seen = [], Counter(), set()
    files = sorted((ROOT / "data" / "extra").glob("facility_*.json")) + sorted((ROOT / "data" / "osm_rail").glob("*_extra.json"))
    for f in files:   # 10-18: osm_rail 의 온천(publicBath)·코인로커·관광안내소·편의점·ATM 도 같은 목록에
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


# ---------- 야경 명소 (10-18) ----------
NIGHT_SKIP = {"灰ヶ峰", "アメリカンビレッジ", "箱根ロープウェイ"}  # 구레시·차탄초(도시 밖), 로프웨이는 밤에 운행 안 함
# 야경 명소는 도시별 대표로 고른 곳인데, 전망대 요소에 위키데이터가 없어 인기도 0인 곳이 많다
# (空中庭園展望台·小樽運河·福岡タワー 등 26곳, 10-19) → 인기도 하한. 전체 상위 10%가 7.
NIGHT_POP_FLOOR = 10


def night_key(s):
    return re.sub(r"[\s・()（）]", "", norm(s))


def load_night_spots():
    f = ROOT / "data" / "extra" / "night_spots_gemini.tsv"
    if not f.exists():
        return []
    rows = []
    for line in f.read_text(encoding="utf-8").splitlines()[1:]:
        c = (line.split("\t") + [""] * 7)[:7]
        if c[0] and c[1] and c[1] not in NIGHT_SKIP:
            rows.append({"city": c[0], "name": c[1], "kind": c[2], "hours": c[3] or None, "fee": c[4] or None,
                         "closed": c[5] or None, "url": c[6] or None})
    return rows


def load_night_osm():
    f = ROOT / "data" / "extra" / "night_spots_osm.json"
    if not f.exists():
        return {}
    return {(o["city"], o["name"]): o["pick"] for o in json.loads(f.read_text(encoding="utf-8")) if o.get("pick")}


def match_night(cands, name):
    """같은 도시 관광지 중 이름이 같은 곳, 없으면 서로 포함하는(3자 이상) 곳 중 가장 긴 이름."""
    k = night_key(name)
    exact = [a for a in cands if night_key(a["name"]) == k]
    if exact:
        return exact[0]
    part = [a for a in cands if len(night_key(a["name"])) >= 3 and (night_key(a["name"]) in k or k in night_key(a["name"]))]
    return max(part, key=lambda a: len(night_key(a["name"])), default=None)


def apply_night_spots(out, rules):
    """야경 명소 표시를 붙이고, 데이터에 없는 곳은 OSM 좌표로 새로 넣는다. (붙임 수, 새로 넣은 수, 좌표 없는 이름들)"""
    for a in out:
        a.setdefault("nightView", False)
        a.setdefault("nightInfo", None)
    osm = load_night_osm()
    tagged, added, missing = 0, 0, []
    for sp in load_night_spots():
        info = {k: sp[k] for k in ("kind", "hours", "fee", "closed", "url")}
        cands = [a for a in out if a["city"] == sp["city"]]
        m = match_night(cands, sp["name"])
        if m:
            m["nightView"], m["nightInfo"] = True, info
            m["popularity"] = max(m.get("popularity") or 0, NIGHT_POP_FLOOR)
            tagged += 1
            continue
        pick = osm.get((sp["city"], sp["name"]))
        if not pick or not in_city(sp["city"], pick["lat"], pick["lng"]):
            missing.append(f"{sp['city']}:{sp['name']}")
            continue
        new = {"name": sp["name"], "nameJa": None, "nameEn": None}
        r = sensitive_of(new, rules)
        if r and r["action"] == "EXCLUDE":
            continue
        near = min(cands, key=lambda a: km((a["lat"], a["lng"]), (pick["lat"], pick["lng"])), default=None)
        out.append({
            "osmId": pick["osmId"], "name": sp["name"], "nameJa": None, "nameEn": None, "nameKo": None,
            "kind": "VIEW", "lat": pick["lat"], "lng": pick["lng"],
            "areas": near["areas"][:1] if near else [sp["city"]], "city": sp["city"],
            "wikidata": None, "popularity": NIGHT_POP_FLOOR, "openingHours": None, "website": sp["url"], "fee": None,
            "historyNote": r["reason"] if r and r["action"] == "NOTE" else None,
            "nightView": True, "nightInfo": info,
        })
        added += 1
    return tagged, added, missing


# ---------- 전망 명소 보충 (osm_view_collect.py, 10-18) ----------
VIEW_NEAR_KM = 0.15   # 이 안에 이미 관광지가 있고 이름이 서로 포함되면 같은 곳으로 본다
# 10-19 수집 결과 검토: 어디인지 알 수 없는 이름, 화재 감시 망루, 등산로 안내판은 뺀다
VIEW_GENERIC = re.compile(r"^((頂上|山頂|[東西南北]側?|中央)?展望(台|所|デッキ|塔|タワー|室|ロビー)(\s*[(（].*[)）])?|火の見櫓|(?i:observation (deck|tower)))$")
VIEW_SIGN = re.compile(r"\d+(\.\d+)?\s*(km|ｋｍ|m|ｍ)|[←→⇔]|分岐|からの|から見える|コース")
VIEW_BAD_WIKIDATA = {"北斎富嶽三十六景"}   # 전망 지점에 그림 작품 위키데이터가 잘못 붙은 곳


def load_views():
    f = ROOT / "data" / "extra" / "osm_views.json"
    return json.loads(f.read_text(encoding="utf-8")) if f.exists() else []


def apply_views(out, rules, views=None):
    """전망 지점·전망 타워를 VIEW 관광지로 추가. 이미 있는 곳(같은 위키데이터, 같은 이름 500m, 150m 안 이름 포함)은 건너뜀. 추가 수."""
    have_wd = {a["wikidata"] for a in out if a.get("wikidata")}
    have_osm = {a.get("osmId") for a in out}
    added = 0
    for v in (load_views() if views is None else views):
        name = norm(v["name"])
        tags = v.get("tags") or {}
        if (v["osmId"] in have_osm or not in_city(v["city"], v["lat"], v["lng"])
                or tags.get("tourism") == "information" or VIEW_GENERIC.match(name) or VIEW_SIGN.search(name)
                or name in VIEW_BAD_WIKIDATA):
            continue
        if v.get("wikidata") and v["wikidata"] in have_wd:
            continue
        r = sensitive_of(v, rules)
        if r and r["action"] == "EXCLUDE":
            continue
        k = night_key(v["name"])
        if same_place(out, v) or any(
                km((a["lat"], a["lng"]), (v["lat"], v["lng"])) < VIEW_NEAR_KM
                and (night_key(a["name"]) in k or k in night_key(a["name"])) for a in out if a["city"] == v["city"]):
            continue
        near = min((a for a in out if a["city"] == v["city"]), key=lambda a: km((a["lat"], a["lng"]), (v["lat"], v["lng"])), default=None)
        t = v.get("tags") or {}
        out.append({
            "osmId": v["osmId"], "name": v["name"], "nameJa": v.get("nameJa"), "nameEn": v.get("nameEn"),
            "nameKo": (v.get("nameKo") or "").split(";")[0].strip() or None,
            "kind": "VIEW", "lat": v["lat"], "lng": v["lng"], "areas": near["areas"][:1] if near else [v["city"]],
            "city": v["city"], "wikidata": v.get("wikidata"), "popularity": v.get("sitelinks", 0),
            "openingHours": t.get("opening_hours"), "website": t.get("website"), "fee": t.get("fee"),
            "historyNote": r["reason"] if r and r["action"] == "NOTE" else None,
            "nightView": False, "nightInfo": None, "officialInfo": None,
        })
        if v.get("wikidata"):
            have_wd.add(v["wikidata"])
        have_osm.add(v["osmId"])
        added += 1
    return added


# ---------- 유명 관광지 보충·꼭 가는 관광지 (osm_popular_collect.py, must_see.tsv, 10-19) ----------
POPULAR_MIN_LINKS = 8   # 위키 링크 수가 이 이상인 곳만 보충 (전체 관광지 상위 10%가 7)
POPULAR_SAME_KM = 1.5   # 같은 이름이면 이 거리 안은 같은 곳 (큰 절·신사는 경내가 넓어 점 위치가 수백 m 차이)
MUST_SEE_FLOOR = 10     # 꼭 가는 관광지 목록에 있는 곳의 인기도 하한
MUST_SEE = SCRIPTS / "must_see.tsv"


def load_popular():
    f = ROOT / "data" / "extra" / "osm_popular.json"
    if not f.exists():
        return [], []
    d = json.loads(f.read_text(encoding="utf-8"))
    return d.get("sweep", []), [m for m in d.get("mustSee", []) if m.get("pick")]


def _new_attraction(v, out, r):
    near = min((a for a in out if a["city"] == v["city"]), key=lambda a: km((a["lat"], a["lng"]), (v["lat"], v["lng"])), default=None)
    t = v.get("tags") or {}
    return {
        "osmId": v["osmId"], "name": v["name"], "nameJa": v.get("nameJa"), "nameEn": v.get("nameEn"),
        "nameKo": (v.get("nameKo") or "").split(";")[0].strip() or None,
        "kind": v.get("kind") or "ATTRACTION", "lat": v["lat"], "lng": v["lng"],
        "areas": near["areas"][:1] if near else [v["city"]], "city": v["city"],
        "wikidata": v.get("wikidata"), "popularity": v.get("sitelinks", 0),
        "openingHours": t.get("opening_hours"), "website": t.get("website"), "fee": t.get("fee"),
        "historyNote": r["reason"] if r and r["action"] == "NOTE" else None,
        "nightView": False, "nightInfo": None, "officialInfo": None,
    }


def apply_popular(out, rules, sweep=None, must=None):
    """위키 링크 수 POPULAR_MIN_LINKS 이상인 관광지와 꼭 가는 관광지(이름으로 찾은 곳)를 합친다.
    이미 있는 곳(같은 osmId·위키데이터)은 건너뛰고, 같은 이름 500m 안에 위키데이터 없는 약한 항목이 있으면 그 항목을 고친다
    (예: 伏見稲荷大社 가 전망 지점(인기도 0)으로만 있던 것). (추가 수, 고친 수)"""
    if sweep is None or must is None:
        s0, m0 = load_popular()
        sweep = s0 if sweep is None else sweep
        must = m0 if must is None else must
    cands = [v for v in sweep if v.get("sitelinks", 0) >= POPULAR_MIN_LINKS] + [m["pick"] for m in must]
    have_wd = {a["wikidata"]: a for a in out if a.get("wikidata")}
    have_osm = {a.get("osmId") for a in out}
    added = upgraded = 0
    for v in sorted(cands, key=lambda x: -x.get("sitelinks", 0)):
        if v["osmId"] in have_osm or not in_city(v["city"], v["lat"], v["lng"]) or (v.get("wikidata") and v["wikidata"] in have_wd):
            continue
        r = sensitive_of(v, rules)
        if r and r["action"] == "EXCLUDE":
            continue
        weak = next((a for a in out if a["city"] == v["city"] and same_name_key(a["name"]) == same_name_key(v["name"])
                     and km((a["lat"], a["lng"]), (v["lat"], v["lng"])) < POPULAR_SAME_KM), None)
        if weak:
            if not weak.get("wikidata") and v.get("wikidata"):
                weak.update({"wikidata": v["wikidata"], "popularity": max(weak.get("popularity") or 0, v.get("sitelinks", 0)),
                             "kind": v.get("kind") or weak["kind"]})
                weak["nameEn"] = weak.get("nameEn") or v.get("nameEn")
                weak["nameKo"] = weak.get("nameKo") or (v.get("nameKo") or "").split(";")[0].strip() or None
                have_wd[v["wikidata"]] = weak
                upgraded += 1
            continue
        a = _new_attraction(v, out, r)
        out.append(a)
        have_osm.add(v["osmId"])
        if v.get("wikidata"):
            have_wd[v["wikidata"]] = a
        added += 1
    return added, upgraded


def load_must_see():
    if not MUST_SEE.exists():
        return []
    rows = []
    for line in MUST_SEE.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            c = (line.split("\t") + ["", ""])[:3]
            rows.append({"city": c[0], "patterns": [night_key(p) for p in c[1].split("|") if p], "ko": c[2]})
    return rows


def apply_must_see(out, rows=None):
    """꼭 가는 관광지 목록의 각 줄마다, 이름이 맞는 관광지 중 인기도가 가장 높은 곳에 mustSee 표시 + 인기도 하한. (표시 수, 못 찾은 줄)"""
    for a in out:
        a.setdefault("mustSee", False)
    hit, missing = 0, []
    for row in (load_must_see() if rows is None else rows):
        cands = [a for a in out if a["city"] == row["city"] and any(
            p in night_key(a["name"]) + "|" + night_key(a.get("nameJa")) + "|" + night_key(a.get("nameEn")) for p in row["patterns"])]
        if not cands:
            missing.append(f"{row['city']}:{row['ko']}")
            continue
        best = max(cands, key=lambda a: (bool(a.get("wikidata")), a.get("popularity") or 0))
        best["mustSee"] = True
        best["popularity"] = max(best.get("popularity") or 0, MUST_SEE_FLOOR)
        hit += 1
    return hit, missing


# ---------- 관광지 사진 (위키데이터 대표 사진 → 위키미디어 위치 검색, 10-19) ----------
PHOTO_KEYS = ("thumb", "width", "height", "credit", "license", "licenseUrl", "artist")
PHOTO_EXCLUDE = SCRIPTS / "photo_exclude.tsv"   # 쓰지 않을 사진 파일 이름 (10-19 사진 검사: 공사 중 등)


def clean_thumb(url):
    """API 가 준 thumb.wikimedia.org 주소(추적용 utm 붙음)를 오래 써 온 upload.wikimedia.org 주소로. 경로는 같다."""
    if not url:
        return url
    url = url.split("?", 1)[0]
    return url.replace("https://thumb.wikimedia.org/wikipedia/commons/thumb/", "https://upload.wikimedia.org/wikipedia/commons/thumb/")


def load_photo_exclude():
    if not PHOTO_EXCLUDE.exists():
        return set()
    return {line.split("\t")[0].strip() for line in PHOTO_EXCLUDE.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")}


def attach_photos(out, media=None, geo=None):
    """photo 필드: 위키데이터 대표 사진(attraction_media.json) → 없으면 근처 사진(attraction_media_geo.json). 출처별 수."""
    ext = ROOT / "data" / "extra"
    if media is None:
        f = ext / "attraction_media.json"
        media = json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}
    if geo is None:
        f = ext / "attraction_media_geo.json"
        geo = json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}
    count = Counter()
    skip = load_photo_exclude()
    for a in out:
        m = media.get(a.get("wikidata") or "") or {}
        g = geo.get(a.get("osmId") or "") or {}
        if m.get("image") in skip:
            m = {}
        if g.get("image") in skip:
            g = {}
        src, pick = ("wikidata", m) if m.get("thumb") else (("nearby", g) if g.get("thumb") else (None, None))
        a["photo"] = {**{k: pick.get(k) for k in PHOTO_KEYS}, "thumb": clean_thumb(pick.get("thumb")), "source": src} if pick else None
        count[src or "none"] += 1
    return count


# ---------- 관광지 공식 정보 (제미나이 조사, 10-17·18) ----------
UNKNOWN = {"", "확인 못 함", "해당 없음", "-", "없음 (확인 못 함)"}


def load_official_info():
    rows = []
    for name in ("attraction_check_gemini.tsv", "attraction_check_gemini_2.tsv"):
        f = ROOT / "data" / "extra" / name
        if not f.exists():
            continue
        for line in f.read_text(encoding="utf-8").splitlines()[1:]:
            c = (line.split("\t") + [""] * 8)[:8]
            if c[0] and c[1]:
                v = lambda x: None if x.strip() in UNKNOWN else x.strip()  # noqa: E731
                rows.append({"city": c[0], "name": c[1], "status": v(c[2]), "reservation": v(c[3]),
                             "closedDays": v(c[4]), "hours": v(c[5]), "fee": v(c[6]), "url": v(c[7])})
    return rows


def apply_official_info(out, rows=None):
    """제미나이로 확인한 운영 상태·예약·휴관일·운영시간·요금·공식 URL 을 officialInfo 로 붙인다. 붙인 수."""
    for a in out:
        a.setdefault("officialInfo", None)
    n = 0
    for r in (load_official_info() if rows is None else rows):
        m = match_night([a for a in out if a["city"] == r["city"]], r["name"])
        if m and m["officialInfo"] is None:
            m["officialInfo"] = {k: r[k] for k in ("status", "reservation", "closedDays", "hours", "fee", "url")}
            n += 1
    return n


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
    night = apply_night_spots(out, rules)
    views = apply_views(out, rules)
    popular = apply_popular(out, rules)
    must = apply_must_see(out)
    official = apply_official_info(out)
    photos = attach_photos(out)
    return out, excluded, noted, len(src), night + (official, views, photos, popular, must)


def main():
    tabelog, t_drop = clean_tabelog()
    chains, c_skip = clean_chains()
    attractions, excluded, noted, a_src, (n_tag, n_add, n_miss, n_official, n_views, n_photos, (n_pop, n_up), (n_must, must_miss)) = clean_attractions()
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
        f"  정기휴일 없음(年中無休) {sum(t['noRegularHoliday'] for t in tabelog)}, 공휴일 휴무 {sum(t['closedOnHolidays'] for t in tabelog)}",
        f"  정기휴일 요일 있음 {sum(1 for t in tabelog if t['closedDays'])}, 부정기 {sum(t['closedIrregular'] for t in tabelog)}, "
        f"점심 예산 있음 {sum(1 for t in tabelog if t['budgetLunchMax'])}, 저녁 1만엔 이하 {sum(1 for t in tabelog if t['budgetDinnerMax'] and t['budgetDinnerMax'] <= 9999)}",
        f"[체인점] {len(chains)}곳 (건너뜀 {dict(c_skip)})",
        f"  종류 {dict(Counter(c['category'] for c in chains))}, 도시별 {dict(Counter(c['city'] for c in chains).most_common())}",
        f"[관광지] 원본 {a_src} → 중복 합친 뒤 {len(attractions) - n_add - n_views - n_pop + len(excluded)} → 제외(민감·폐관·관람 불가) {len(excluded)} → 야경 추가 {n_add} → 전망 보충 {n_views} → 유명 보충 {n_pop} → {len(attractions)}곳 (역사 안내 표시 {noted})",
        f"  종류 {dict(Counter(a['kind'] for a in attractions).most_common())}",
        f"  인기도 있음(위키데이터 장소) {sum(1 for a in attractions if a['popularity'])}, 한국어 이름 {sum(1 for a in attractions if a['nameKo'])}",
        f"  야경 명소 표시 {n_tag}, 새로 추가 {n_add}, 좌표 없음 {len(n_miss)} {n_miss}",
        f"  전망 명소 보충 추가 {n_views} (osm_views.json)",
        f"  유명 관광지 보충 추가 {n_pop}, 약한 항목 고침 {n_up} (osm_popular.json)",
        f"  꼭 가는 관광지 {n_must}곳 표시, 못 찾음 {len(must_miss)} {must_miss}",
        f"  사진: 위키데이터 {n_photos['wikidata']}, 근처 사진 {n_photos['nearby']}, 없음 {n_photos['none']}",
        f"  공식 정보(제미나이 조사) 붙임 {n_official}, 운영시간(OSM) 있음 {sum(1 for a in attractions if a['openingHours'])}",
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
