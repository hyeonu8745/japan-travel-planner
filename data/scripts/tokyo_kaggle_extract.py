"""
캐글 도쿄 타베로그 CSV(data/raw/Tokyo_Restaurant_Reviews_Tabelog.csv)에서 도쿄 9개 구, 평점 3.4 이상(지금 DB와 같은 기준)을 꺼내
Apify 수집 파일과 같은 모양(data/tabelog_raw/tokyo_<구>_p00-00_kaggle.json)으로 만든다.
→ 그다음 tabelog_geocode.py(국토지리원 좌표) → data_clean.py 를 그대로 돌리면 다른 도시와 똑같이 정리된다.

- 지금 DB의 6개 구(지요다·주오·미나토·신주쿠·시부야·다이토)도 같이 꺼낸다: 좌표를 Google Geocoding 값에서
  국토지리원 값으로 바꾸기 위해서 (Google 좌표 저장은 약관 문제, 인수인계 문서 참고)
- 추가 3개 구: 스미다·고토·도시마

    프로젝트 루트에서:  python data/scripts/tokyo_kaggle_extract.py
"""
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "data" / "raw" / "Tokyo_Restaurant_Reviews_Tabelog.csv"
OUT = ROOT / "data" / "tabelog_raw"
MIN_RATING = 3.4   # 지금 DB(preprocess_and_geocode.py)와 같은 기준. 3.2로 하면 6개 구만 1만 9천 곳
WARDS = {"千代田区": "chiyoda", "中央区": "chuo", "港区": "minato", "新宿区": "shinjuku", "渋谷区": "shibuya",
         "台東区": "taito", "墨田区": "sumida", "江東区": "koto", "豊島区": "toshima"}


def num(v):
    try:
        f = float(v)
    except (TypeError, ValueError):
        return None
    return None if f != f else f   # NaN → None


def main():
    rows = list(csv.DictReader(SRC.open(encoding="utf-8")))
    by_ward = {w: [] for w in WARDS}
    for r in rows:
        ward = (r.get("municipalities_1") or "").strip()
        rating = num(r.get("rating_val"))
        if r.get("prefecture") != "東京" or ward not in WARDS or rating is None or rating < MIN_RATING:
            continue
        dist = num(r.get("nearest_distance"))
        station = (r.get("nearest_station") or "").strip()
        holiday = (r.get("holiday") or "").strip()
        by_ward[ward].append({
            "restaurantName": (r.get("name") or "").strip(),
            "restaurantUrl": f"https://tabelog.com/tokyo/{r['store_id'].strip()}/",   # 저장 형식만 맞춤 (끝 숫자 = store_id)
            "restaurantRating": rating,
            "restaurantGenre": "、".join(g.strip() for g in (r.get("genre") or "").split("、") if g.strip()),
            "restaurantArea": "東京",
            "totalReviewCount": int(num(r.get("review_cnt")) or 0),
            "address": (r.get("address") or "").strip(),
            "nearestStation": f"{station}から{int(dist)}m" if station and dist is not None else station,
            "businessHours": None,
            "holiday": None if holiday in ("", "-") else holiday,
            "budgetDinner": None if (r.get("budget_dinner") or "-").strip() == "-" else r["budget_dinner"].strip(),
            "budgetLunch": None if (r.get("budget_lunch") or "-").strip() == "-" else r["budget_lunch"].strip(),
            "source": "kaggle",
        })
    OUT.mkdir(parents=True, exist_ok=True)
    total = 0
    for ward, items in by_ward.items():
        f = OUT / f"tokyo_{WARDS[ward]}_p00-00_kaggle.json"
        f.write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding="utf-8")
        total += len(items)
        print(f"{ward} ({WARDS[ward]}): {len(items)}곳 → {f.name}")
    print(f"합계 {total}곳. 다음: python data/scripts/tabelog_geocode.py → python data/scripts/data_clean.py")


if __name__ == "__main__":
    main()
