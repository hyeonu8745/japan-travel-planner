"""
타베로그 식당 ↔ Google 장소 매칭 정확도 측정 (샘플 50곳, 1회성 조사용)

실행 (프로젝트 루트에서):
    python data/scripts/match_sample_test.py

- 키: 환경변수 GOOGLE_MAPS_API_KEY, 없으면 프로젝트 루트 .env 에서 읽는다 (화면에 출력하지 않음)
- 호출: Text Search (New) 50건. FieldMask에 displayName·location·formattedAddress가 있어서
  Google 기준 "Text Search Pro" 등급 (월 5,000건 무료) → 이 스크립트는 무료 한도 안
- 결과: data/processed/match_sample_result.csv (Google 이름·주소 포함, .gitignore 대상)
  → Google 약관상 장기 보관 금지. 검토가 끝나면 지운다.
"""
import csv
import math
import os
import random
import re
import sys
import json
import unicodedata
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INPUT_CSV = os.path.join(ROOT, "data", "processed", "restaurants_tokyo_filtered_geocoded.csv")
OUTPUT_CSV = os.path.join(ROOT, "data", "processed", "match_sample_result.csv")
SAMPLE_SIZE = 50
SEED = 20261009
BIAS_RADIUS_M = 300.0
URL = "https://places.googleapis.com/v1/places:searchText"
FIELD_MASK = "places.id,places.displayName,places.location,places.formattedAddress"


def load_key() -> str:
    key = os.environ.get("GOOGLE_MAPS_API_KEY", "").strip()
    if key:
        return key
    env_path = os.path.join(ROOT, ".env")
    if os.path.exists(env_path):
        with open(env_path, encoding="utf-8") as f:
            for line in f:
                if line.startswith("GOOGLE_MAPS_API_KEY="):
                    return line.split("=", 1)[1].strip().strip('"').strip("'")
    sys.exit("GOOGLE_MAPS_API_KEY를 찾지 못했습니다 (.env 또는 환경변수).")


def haversine_m(lat1, lng1, lat2, lng2) -> float:
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def normalize(name: str) -> str:
    s = unicodedata.normalize("NFKC", name or "").lower()
    s = re.sub(r"[\s　・·\-‐－_/()（）\[\]【】「」『』'\".,、。!！?？&＆]", "", s)
    s = re.sub(r"(本店|支店|店)$", "", s)
    return s


def bigram_dice(a: str, b: str) -> float:
    a, b = normalize(a), normalize(b)
    if not a or not b:
        return 0.0
    if a in b or b in a:
        return 1.0
    ga = {a[i:i + 2] for i in range(len(a) - 1)} or {a}
    gb = {b[i:i + 2] for i in range(len(b) - 1)} or {b}
    return 2 * len(ga & gb) / (len(ga) + len(gb))


def search(key: str, row: dict) -> list:
    body = {
        "textQuery": f"{row['name']} {row['address']}",
        "languageCode": "ja",
        "regionCode": "JP",
        "pageSize": 3,
        "locationBias": {"circle": {
            "center": {"latitude": float(row["latitude"]), "longitude": float(row["longitude"])},
            "radius": BIAS_RADIUS_M}},
    }
    headers = {"X-Goog-Api-Key": key, "X-Goog-FieldMask": FIELD_MASK, "Content-Type": "application/json"}
    req = urllib.request.Request(URL, data=json.dumps(body).encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return json.loads(res.read().decode("utf-8")).get("places", [])
    except urllib.error.HTTPError as e:
        print(f"  ! HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:200]}")
        return []


def main():
    # Windows 콘솔(cp949)에서 일본어 이름 출력 오류 방지
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    key = load_key()
    with open(INPUT_CSV, encoding="utf-8-sig") as f:
        rows = [r for r in csv.DictReader(f) if r.get("latitude") and r.get("longitude")]
    random.Random(SEED).shuffle(rows)
    sample = rows[:SAMPLE_SIZE]

    out = []
    for i, row in enumerate(sample, 1):
        places = search(key, row)
        lat, lng = float(row["latitude"]), float(row["longitude"])
        best = None
        for rank, p in enumerate(places, 1):
            loc = p.get("location", {})
            dist = haversine_m(lat, lng, loc.get("latitude", 0), loc.get("longitude", 0))
            g_name = p.get("displayName", {}).get("text", "")
            sim = bigram_dice(row["name"], g_name)
            cand = {"rank": rank, "place_id": p.get("id", ""), "g_name": g_name,
                    "g_address": p.get("formattedAddress", ""), "dist_m": round(dist), "name_sim": round(sim, 2)}
            if best is None or (sim, -dist) > (best["name_sim"], -best["dist_m"]):
                best = cand
        rec = {"no": i, "store_id": row["store_id"], "name": row["name"], "address": row["address"],
               "results": len(places)}
        rec.update(best or {"rank": "", "place_id": "", "g_name": "", "g_address": "", "dist_m": "", "name_sim": ""})
        out.append(rec)
        print(f"{i:2d}. {row['name']}  →  {rec['g_name'] or '(없음)'}  | {rec['dist_m']}m | 이름 {rec['name_sim']} | 순위 {rec['rank']}")

    with open(OUTPUT_CSV, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(out[0].keys()))
        w.writeheader()
        w.writerows(out)

    found = [r for r in out if r["place_id"]]
    def count(cond):
        return sum(1 for r in found if cond(r))
    print("\n=== 요약 ===")
    print(f"검색 결과 있음: {len(found)}/{len(out)}")
    for d in (50, 100, 150, 300):
        for s in (0.5, 0.7):
            print(f"  거리 ≤{d}m & 이름 ≥{s}: {count(lambda r, d=d, s=s: r['dist_m'] <= d and r['name_sim'] >= s)}")
    print(f"  1순위가 아닌 결과를 고른 경우: {count(lambda r: r['rank'] != 1)}")
    print(f"\n결과 파일: {OUTPUT_CSV}  (검토 후 삭제)")


if __name__ == "__main__":
    main()
