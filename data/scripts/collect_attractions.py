# -*- coding: utf-8 -*-
"""
Google Places API (New) - Text Search로 도쿄 6개 구 관광지 데이터 수집

사용법:
    1) Colab Secrets에 GOOGLE_MAPS_API_KEY 등록 (이미 되어 있음)
    2) 아래 코드를 Colab 셀에 붙여넣고 실행
    3) 완료 후 attractions_tokyo.csv 다운로드

동작 방식:
    - 6개 관광지 중심 구에 대해 "관광명소" 텍스트 검색
    - Places API (New)의 searchText 엔드포인트 사용
    - 위경도가 이미 응답에 포함되어 있어 별도 Geocoding 불필요
    - 결과를 CSV로 저장
"""

import os
import time
import requests
import pandas as pd

# =========================
# CONFIG
# =========================
API_KEY = os.environ.get("GOOGLE_MAPS_API_KEY", "")
SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"

# Restaurant 데이터의 municipalities_1 값과 표기를 맞춤
WARDS = ["千代田区", "中央区", "港区", "新宿区", "渋谷区", "台東区"]

RESULTS_PER_WARD = 20   # 구당 최대 검색 결과 수
SLEEP_SEC = 0.3         # 요청 사이 대기시간
OUTPUT_CSV = "attractions_tokyo.csv"

FIELD_MASK = ",".join([
    "places.id",
    "places.displayName",
    "places.formattedAddress",
    "places.location",
    "places.rating",
    "places.userRatingCount",
    "places.types",
])


def search_attractions(ward: str) -> list:
    """한 개 구에 대해 관광명소 텍스트 검색"""
    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": API_KEY,
        "X-Goog-FieldMask": FIELD_MASK,
    }
    body = {
        "textQuery": f"{ward} 東京 観光名所",
        "languageCode": "ja",
        "maxResultCount": RESULTS_PER_WARD,
    }

    try:
        resp = requests.post(SEARCH_URL, headers=headers, json=body, timeout=15)
        data = resp.json()

        if "places" not in data:
            print(f"  [실패] {ward} -> 응답: {data}")
            return []

        results = []
        for place in data["places"]:
            results.append({
                "ward": ward,
                "place_id": place.get("id"),
                "name": place.get("displayName", {}).get("text"),
                "address": place.get("formattedAddress"),
                "latitude": place.get("location", {}).get("latitude"),
                "longitude": place.get("location", {}).get("longitude"),
                "rating": place.get("rating"),
                "review_cnt": place.get("userRatingCount"),
                "types": ",".join(place.get("types", [])),
            })
        return results

    except Exception as e:
        print(f"  [오류] {ward} -> {e}")
        return []


def main():
    if not API_KEY:
        raise RuntimeError(
            "GOOGLE_MAPS_API_KEY 환경변수가 설정되지 않았습니다."
        )

    all_results = []

    for ward in WARDS:
        print(f"[검색 중] {ward}...")
        results = search_attractions(ward)
        print(f"  -> {len(results)}건 수집")
        all_results.extend(results)
        time.sleep(SLEEP_SEC)

    df = pd.DataFrame(all_results)

    before = len(df)
    df = df.drop_duplicates(subset=["place_id"])
    print(f"[중복 제거] {before}건 -> {len(df)}건")

    df.to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")
    print(f"[완료] 저장: {OUTPUT_CSV} ({len(df)}건)")


if __name__ == "__main__":
    main()
