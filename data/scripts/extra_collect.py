"""
확장용 추가 데이터를 한 번에 받는다 (10-16). 키·가입 없음, 무료. osm_rail_collect.py 다음에 실행한다.

    프로젝트 루트에서:  python data/scripts/extra_collect.py
    한 부분만:          python data/scripts/extra_collect.py bus facility holidays wiki mlit
    정리만 다시:        python data/scripts/extra_collect.py --build

받는 것 (부분마다 따로 실패해도 나머지는 계속, 받은 것은 다음 실행에서 건너뜀)
1. bus       : 버스 중심 소도시의 버스 노선·정류장 (OSM) — 가나자와·다카야마·벳푸·유후인·하코네·나라·가마쿠라·가와구치코
2. facility  : 13개 지역의 편의 시설 (OSM) — 환전소, 자전거 대여, 공중화장실 / 역 출입구, 엘리베이터 / 주차장(렌터카용)
3. holidays  : 일본 공휴일 (내각부 공휴일 CSV, 1955년~내년) → 식당 "공휴일 휴무" 판단용
4. wiki      : 확장 관광지의 사진(위키미디어 공용, 라이선스·작가 포함)과 한국어 위키백과 첫 문단 (Wikidata로 연결)
5. mlit      : 국토교통성 국토수치정보 철도 데이터(N02, 노선·역 공식 데이터) → OSM 노선망 검증용

출력 (모두 .gitignore, data/extra/)
- bus_<지역>_routes.json / bus_<지역>_stops.json   (osm_rail과 같은 형식)
- facility_<지역>.json : {exchange:[], bicycle:[], toilets:[], entrances:[], elevators:[], parking:[]}
- japan_holidays.json  : [{"date": "2026-01-01", "name": "元日"}, ...]
- attraction_media.json: {"Q123": {image, thumb, width, height, license, licenseUrl, artist, credit, koTitle, koSummary, koUrl}}
- mlit/N02-xx_GML.zip (+ 압축 푼 파일), mlit_stations.json (역 이름·회사·노선·좌표)
- extra_report.txt

출처 표시 의무: OSM "© OpenStreetMap contributors"(ODbL), 사진은 작가·라이선스(CC BY-SA 등) 그대로,
위키백과 글은 CC BY-SA("출처: 한국어 위키백과"), 국토수치정보는 "国土数値情報(国土交通省)".
(실제 운임·시간표 ODPT는 개발자 등록·키가 필요해서 이 파일에 없다)
"""
import csv
import io
import json
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
try:   # 10-16: Windows의 Python은 일본 관공서 사이트 인증서를 확인하지 못할 때가 있다 → 있으면 Windows 인증서 저장소를 쓴다 (pip install truststore)
    import truststore
    truststore.inject_into_ssl()
except ImportError:
    pass
import osm_rail_collect as orc   # Overpass 요청·조각·이어 받기·노선 정리는 그대로 쓴다

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "data" / "extra"
ATTRACTIONS = ROOT / "data" / "processed" / "clean_attractions.json"
UA = "TabiMate-student-project/1.0 (https://github.com/hyeonu8745/japan-travel-planner; non-commercial)"   # 위키미디어는 연락처가 있는 UA를 요구한다

# ===================== 1. 소도시 버스 =====================

BUS_REGIONS = {   # (남, 서, 북, 동), 조각 수
    "kanazawa":    ((36.50, 136.58, 36.62, 136.72), 1),
    "takayama":    ((36.10, 137.20, 36.20, 137.32), 1),
    "beppu":       ((33.22, 131.42, 33.37, 131.53), 1),
    "yufuin":      ((33.22, 131.30, 33.32, 131.42), 1),
    "hakone":      ((35.18, 138.95, 35.30, 139.17), 1),
    "nara":        ((34.64, 135.76, 34.72, 135.88), 1),
    "kamakura":    ((35.29, 139.50, 35.36, 139.58), 1),
    "kawaguchiko": ((35.45, 138.70, 35.56, 138.86), 1),
}


def collect_bus(build_only):
    report = []
    for region, (box, n) in BUS_REGIONS.items():
        key = f"bus_{region}"
        if not build_only:
            print(f"[버스] {region}")
            orc.fetch(key, box, n, {"bus": orc.q_bus})
        rows = orc.load(key, "bus")
        if not rows:
            report.append(f"[버스 {region}] 받은 데이터 없음")
            continue
        routes, stops = orc.build_rail(key, rows)
        write(f"bus_{region}_routes.json", routes)
        write(f"bus_{region}_stops.json", {str(k): v for k, v in stops.items()})
        few = sum(1 for r in routes if len(r["stops"]) < 3 and len(r["platforms"]) < 3)
        report.append(f"[버스 {region}] 노선 {len(routes)}, 정류장 {len(stops)}, 정류장 3개 미만 노선 {few}")
    return report


# ===================== 2. 편의 시설 =====================

def q_facility(b):
    return f"""{orc.HEAD}
(
  nwr["amenity"="bureau_de_change"]({b});
  nwr["amenity"="bicycle_rental"]({b});
  nwr["amenity"="toilets"]({b});
);
out center tags;"""


def q_access(b):
    return f"""{orc.HEAD}
(
  node["railway"~"^(subway_entrance|train_station_entrance)$"]({b});
  node["public_transport"="entrance"]({b});
  node["highway"="elevator"]({b});
);
out body;"""


def q_parking(b):
    # 렌터카용: 사유지·직원용은 빼고, 이름·요금·수용 대수 중 하나라도 있는 곳만 (코인 주차 조각까지 다 받으면 너무 많다)
    return f"""{orc.HEAD}
nwr["amenity"="parking"]["access"!~"^(private|no|customers)$"]["name"]({b});
out center tags;
nwr["amenity"="parking"]["access"!~"^(private|no|customers)$"][!"name"]["fee"]["capacity"]({b});
out center tags;"""


FACILITY_KINDS = {"facility": q_facility, "access": q_access, "parking": q_parking}


def collect_facility(build_only):
    report = []
    for region, (box, n) in orc.REGIONS.items():
        if not build_only:
            print(f"[편의 시설] {region} (조각 {n * n}개 × 3종류)")
            orc.fetch(region, box, n, FACILITY_KINDS)
        out = {"exchange": [], "bicycle": [], "toilets": [], "entrances": [], "elevators": [], "parking": []}
        for kind in FACILITY_KINDS:
            for e in orc.load(region, kind):
                tags = e.get("tags", {})
                lat, lng = orc.latlng(e)
                item = {"osmId": f"{e['type']}/{e['id']}", **orc.names(tags), "lat": lat, "lng": lng,
                        "operator": tags.get("operator"), "openingHours": tags.get("opening_hours"),
                        "fee": tags.get("fee"), "wheelchair": tags.get("wheelchair")}
                a = tags.get("amenity")
                if a == "bureau_de_change":
                    out["exchange"].append(item)
                elif a == "bicycle_rental":
                    item["network"] = tags.get("network")
                    out["bicycle"].append(item)
                elif a == "toilets":
                    out["toilets"].append(item)
                elif a == "parking":
                    item.update({"capacity": tags.get("capacity"), "parking": tags.get("parking"),
                                 "maxstay": tags.get("maxstay"), "charge": tags.get("charge")})
                    out["parking"].append(item)
                elif tags.get("highway") == "elevator":
                    item["level"] = tags.get("level")
                    out["elevators"].append(item)
                else:   # 역 출입구
                    item.update({"ref": tags.get("ref"), "exitTo": tags.get("exit_to")})
                    out["entrances"].append(item)
        write(f"facility_{region}.json", out)
        report.append(f"[편의 시설 {region}] " + " · ".join(f"{k} {len(v)}" for k, v in out.items()))
    return report


# ===================== 3. 일본 공휴일 =====================

HOLIDAY_URL = "https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv"   # 내각부, Shift_JIS, "国民の祝日・休日月日,国民の祝日・休日名称"
HOLIDAY_FALLBACK = "https://holidays-jp.github.io/api/v1/date.json"         # 내각부 CSV를 옮겨 둔 공개 API (작년~내년), 내각부 접속이 안 될 때


def collect_holidays(build_only):
    f = OUT / "japan_holidays.json"
    if not build_only and not f.exists():
        rows = []
        raw = get(HOLIDAY_URL)
        if raw:
            text = raw.decode("cp932")
            for i, line in enumerate(csv.reader(io.StringIO(text))):
                if i == 0 or len(line) < 2:
                    continue
                y, m, d = (int(x) for x in line[0].strip().split("/"))
                rows.append({"date": f"{y:04d}-{m:02d}-{d:02d}", "name": line[1].strip()})
        else:
            print("    내각부 접속 실패 → holidays-jp(작년~내년)로 받아요")
            data = get_json(HOLIDAY_FALLBACK) or {}
            rows = [{"date": k, "name": v} for k, v in sorted(data.items())]
        if rows:
            write("japan_holidays.json", rows)
    if not f.exists():
        return ["[공휴일] 받은 데이터 없음"]
    rows = json.loads(f.read_text(encoding="utf-8"))
    years = sorted({r["date"][:4] for r in rows})
    this_year = [r for r in rows if r["date"].startswith(str(datetime.now().year))]
    return [f"[공휴일] {len(rows)}개 ({years[0]}~{years[-1]}), 올해 {len(this_year)}개"]


# ===================== 4. 관광지 사진·한국어 소개 (Wikidata) =====================

WIKIDATA_API = "https://www.wikidata.org/w/api.php"
COMMONS = "https://commons.wikimedia.org/w/api.php"
KO_SUMMARY = "https://ko.wikipedia.org/api/rest_v1/page/summary/"


def collect_wiki(build_only):
    f = OUT / "attraction_media.json"
    media = json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}
    if not build_only:
        if not ATTRACTIONS.exists():
            return ["[위키] clean_attractions.json 이 없어요 (data_clean.py 먼저)"]
        qids = sorted({a["wikidata"] for a in json.loads(ATTRACTIONS.read_text(encoding="utf-8")) if a.get("wikidata")})
        todo = [q for q in qids if q not in media]
        print(f"[위키] 관광지 {len(qids)}곳 중 새로 {len(todo)}곳")
        # (1) Wikidata: 대표 사진(P18)과 한국어 위키백과 제목
        # 10-16: 검색 서버(SPARQL)는 "1분에 1번"으로 막혀서, 일반 API(wbgetentities, 한 번에 50곳)로 받는다
        for i in range(0, len(todo), 50):
            batch = todo[i:i + 50]
            data = get_json(WIKIDATA_API + "?" + urllib.parse.urlencode({
                "action": "wbgetentities", "ids": "|".join(batch), "props": "claims|sitelinks",
                "sitefilter": "kowiki", "format": "json", "formatversion": 2}))
            if data is None:
                continue
            for q in batch:
                ent = data.get("entities", {}).get(q, {})
                m = media.setdefault(q, {})
                p18 = ent.get("claims", {}).get("P18") or []
                if p18 and not m.get("image"):
                    m["image"] = p18[0].get("mainsnak", {}).get("datavalue", {}).get("value")   # 파일 이름
                ko = ent.get("sitelinks", {}).get("kowiki", {}).get("title")
                if ko and not m.get("koTitle"):
                    m["koTitle"] = ko
            print(f"  Wikidata {min(i + 50, len(todo))}/{len(todo)}")
            save_media(media)
            time.sleep(1)
        # (2) 위키미디어 공용: 사진 주소(800px)·라이선스·작가
        need = [q for q, m in media.items() if m.get("image") and "thumb" not in m]
        for i in range(0, len(need), 50):
            batch = need[i:i + 50]
            titles = "|".join("File:" + media[q]["image"] for q in batch)
            data = get_json(COMMONS + "?" + urllib.parse.urlencode({
                "action": "query", "titles": titles, "prop": "imageinfo", "iiprop": "url|extmetadata|size",
                "iiurlwidth": 800, "format": "json", "formatversion": 2}))
            if data is None:
                continue
            by_title = {}
            for p in data.get("query", {}).get("pages", []):
                info = (p.get("imageinfo") or [{}])[0]
                meta = info.get("extmetadata", {})
                by_title[p.get("title", "").replace("File:", "").replace("_", " ")] = {
                    "thumb": info.get("thumburl"), "width": info.get("thumbwidth"), "height": info.get("thumbheight"),
                    "credit": info.get("descriptionurl"),
                    "license": strip_html(meta.get("LicenseShortName", {}).get("value")),
                    "licenseUrl": meta.get("LicenseUrl", {}).get("value"),
                    "artist": strip_html(meta.get("Artist", {}).get("value")),
                }
            for q in batch:
                media[q].update(by_title.get(media[q]["image"].replace("_", " "), {"thumb": None}))
            print(f"  사진 정보 {min(i + 50, len(need))}/{len(need)}")
            save_media(media)
            time.sleep(1)
        # (3) 한국어 위키백과 첫 문단
        need = [q for q, m in media.items() if m.get("koTitle") and "koSummary" not in m]
        for n, q in enumerate(need, 1):
            data = get_json(KO_SUMMARY + urllib.parse.quote(media[q]["koTitle"].replace(" ", "_"), safe=""))
            media[q]["koSummary"] = (data or {}).get("extract")
            media[q]["koUrl"] = ((data or {}).get("content_urls") or {}).get("desktop", {}).get("page")
            if n % 50 == 0 or n == len(need):
                print(f"  한국어 소개 {n}/{len(need)}")
                save_media(media)
            time.sleep(0.3)
        save_media(media)
    with_image = sum(1 for m in media.values() if m.get("thumb"))
    with_ko = sum(1 for m in media.values() if m.get("koSummary"))
    return [f"[위키] 관광지 {len(media)}곳 중 사진 {with_image}곳, 한국어 소개 {with_ko}곳"]


def save_media(media):
    write("attraction_media.json", media)


def strip_html(s):
    if not s:
        return s
    import re
    return re.sub(r"<[^>]+>", "", s).strip()


# ===================== 5. 국토수치정보 철도 (N02) =====================

MLIT_URLS = [f"https://nlftp.mlit.go.jp/ksj/gml/data/N02/N02-{y}/N02-{y}_GML.zip" for y in (25, 24, 23, 22)]


def collect_mlit(build_only):
    d = OUT / "mlit"
    d.mkdir(parents=True, exist_ok=True)
    zips = sorted(d.glob("N02-*_GML.zip"))
    if not build_only and not zips:
        for url in MLIT_URLS:   # 가장 최신 판부터 시도
            print(f"[국토수치정보] {url}")
            raw = get(url, quiet=True)
            if raw and raw[:2] == b"PK":
                (d / url.rsplit("/", 1)[-1]).write_bytes(raw)
                break
        zips = sorted(d.glob("N02-*_GML.zip"))
    if not zips:
        return ["[국토수치정보] 받지 못함 (주소가 바뀌었을 수 있어요: https://nlftp.mlit.go.jp/ksj/ 에서 N02 직접 받기)"]
    z = zipfile.ZipFile(zips[-1])
    z.extractall(d)
    station_files = [n for n in z.namelist() if n.lower().endswith(".geojson") and "station" in n.lower()]
    stations = []
    for name in station_files:
        g = json.loads((d / name).read_text(encoding="utf-8"))
        for feat in g.get("features", []):
            p = feat.get("properties", {})
            coords = feat.get("geometry", {}).get("coordinates") or []
            pt = coords[0] if coords and isinstance(coords[0], list) else coords
            stations.append({"name": p.get("N02_005"), "line": p.get("N02_003"), "operator": p.get("N02_004"),
                             "lng": pt[0] if pt else None, "lat": pt[1] if pt else None})
    if stations:
        write("mlit_stations.json", stations)
    return [f"[국토수치정보] {zips[-1].name}, 역 {len(stations)}곳"
            + ("" if station_files else " (GeoJSON 없음 → 압축 푼 GML로 나중에 정리)")]


# ===================== 공통 =====================

def get(url, quiet=False):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as res:
                return res.read()
        except Exception as e:
            if (quiet and "404" in str(e)) or "CERTIFICATE_VERIFY_FAILED" in str(e):
                if "CERTIFICATE" in str(e):
                    print(f"    인증서 확인 실패: {url.split('/')[2]} (pip install truststore 후 다시 실행하면 대부분 해결)")
                return None
            print(f"    실패({str(e)[:60]}), {10 * (attempt + 1)}초 뒤 다시")
            time.sleep(10 * (attempt + 1))
    return None


def get_json(url, accept="application/json"):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": accept})
            with urllib.request.urlopen(req, timeout=120) as res:
                return json.loads(res.read().decode("utf-8"))
        except Exception as e:
            if "404" in str(e) or "CERTIFICATE_VERIFY_FAILED" in str(e):
                if "CERTIFICATE" in str(e):
                    print(f"    인증서 확인 실패: {url.split('/')[2]} (pip install truststore 후 다시 실행하면 대부분 해결)")
                return None
            wait = 60 if "429" in str(e) else 10 * (attempt + 1)
            print(f"    실패({str(e)[:60]}), {wait}초 뒤 다시")
            time.sleep(wait)
    return None


def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


PARTS = {"bus": collect_bus, "facility": collect_facility, "holidays": collect_holidays,
         "wiki": collect_wiki, "mlit": collect_mlit}


def main():
    orc.RAW.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    build_only = "--build" in sys.argv
    chosen = [a for a in sys.argv[1:] if a in PARTS] or list(PARTS)
    # 빨리 끝나는 것부터 (공휴일·국토수치정보·위키 → 버스 → 편의 시설)
    order = [p for p in ("holidays", "mlit", "wiki", "bus", "facility") if p in chosen]
    report = [f"추가 데이터 정리 ({datetime.now():%Y-%m-%d %H:%M})"]
    for part in order:
        try:
            report += PARTS[part](build_only)
        except Exception as e:   # 한 부분이 실패해도 나머지는 계속
            report.append(f"[{part}] 실패: {e}")
            print(f"[{part}] 실패: {e} → 다음 부분으로")
    (OUT / "extra_report.txt").write_text("\n".join(report), encoding="utf-8")
    print("\n" + "\n".join(report))


if __name__ == "__main__":
    main()
