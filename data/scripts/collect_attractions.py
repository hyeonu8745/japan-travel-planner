# -*- coding: utf-8 -*-
"""
Google Places API (New) - Text Search로 도쿄 관광지 데이터 수집 (10-10 범위 확대판)

사용법 (Colab):
    1) Colab Secrets(왼쪽 열쇠 아이콘)에 GOOGLE_MAPS_API_KEY 등록, "노트북 액세스" 켜기
       (스크립트가 Secrets에서 직접 읽는다)
    2) 이 파일 전체를 셀에 붙여넣고 실행
    2-1) (권장) 기존 data/processed/attractions_tokyo.csv 를 Colab에 attractions_tokyo_old.csv 이름으로 올려두면
         새 결과와 합쳐서 저장한다 (새 검색에 안 나온 기존 관광지도 CSV에 남는다. DB를 새로 만들 때 대비)
    3) 완료 후 attractions_tokyo.csv 다운로드 → data/processed/attractions_tokyo.csv 에 덮어쓰기
       (서버를 다시 켜면 DB에 없는 place_id만 추가된다. 기존 관광지는 지우지 않는다)

예전 방식(10-10 이전): 6개 구 × "観光名所" 검색 1번 × 20개 = 119곳.
    → 시부야 9곳·주오 7곳(리뷰 100개 이상)처럼 지역마다 후보가 모자랐고,
      오다이바 대부분(江東区), 스카이트리(墨田区), 아키하바라 전자상가가 빠져 있었다.

바뀐 방식:
    - 구 8개 (江東区·墨田区 추가)
    - 구마다 종류별 검색어 5개 + 지역 이름 검색 몇 개
    - 검색마다 다음 페이지까지 받아 최대 60개
    - ward는 검색한 구가 아니라 주소의 "東京都○○区"에서 읽는다 (다른 구 장소도 검색에 섞여 나온다)
    - 숙박·식당만인 곳은 뺀다 (관광지 성격 type이 같이 붙은 곳은 남김: 新宿ゴールデン街 등)
    - 리뷰 수 필터와 같은 장소 합치기는 서버(AttractionScorer)가 한다

호출 수: 검색어 약 46개 × 최대 3페이지 ≈ 140번 이하.
    rating·userRatingCount를 받으므로 Text Search Enterprise 요금(무료 월 1,000건) 안에서 끝난다.
"""

import os
import re
import time
import requests
import pandas as pd

# =========================
# CONFIG
# =========================
def load_api_key() -> str:
    """환경변수 → Colab Secrets 순서로 찾는다 (Colab은 Secrets를 환경변수로 넣어주지 않는다)"""
    key = os.environ.get("GOOGLE_MAPS_API_KEY", "")
    if key:
        return key
    try:
        from google.colab import userdata
        return userdata.get("GOOGLE_MAPS_API_KEY") or ""
    except Exception:
        return ""


API_KEY = load_api_key()
SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"

# Restaurant 데이터의 municipalities_1 값과 표기를 맞춤
WARDS = ["千代田区", "中央区", "港区", "新宿区", "渋谷区", "台東区", "江東区", "墨田区"]

# 구마다 붙이는 검색어 (관광명소만으로는 종류가 한쪽으로 몰렸다)
WARD_QUERIES = ["観光名所", "夜景スポット", "ショッピング 名所", "博物館 美術館", "神社 寺 庭園"]

# 구 이름으로는 잘 안 잡히는 지역
AREA_QUERIES = [
    "秋葉原 電気街 観光",
    "秋葉原 フィギュア ショップ",
    "お台場 観光名所",
    "豊洲 観光名所",
    "東京スカイツリー 周辺 観光",
    "上野 観光名所",
]

PAGE_SIZE = 20
MAX_PAGES = 3           # 검색 하나당 최대 60개
SLEEP_SEC = 0.3         # 요청 사이 대기
PAGE_SLEEP_SEC = 2.0    # 다음 페이지 토큰이 유효해질 때까지 잠깐 기다림
OUTPUT_CSV = "attractions_tokyo.csv"
OLD_CSV = "attractions_tokyo_old.csv"

FIELD_MASK = ",".join([
    "places.id",
    "places.displayName",
    "places.formattedAddress",
    "places.location",
    "places.rating",
    "places.userRatingCount",
    "places.types",
    "nextPageToken",
])

# 관광지로 보지 않는 곳: 이 type만 있고 관광지 성격 type이 없으면 뺀다
EXCLUDE_TYPES = {"lodging", "hotel", "restaurant", "cafe", "bar", "food"}
ATTRACTION_TYPES = {"tourist_attraction", "museum", "art_museum", "art_gallery", "park", "garden",
                    "observation_deck", "shinto_shrine", "buddhist_temple", "place_of_worship",
                    "historical_landmark", "historical_place", "cultural_landmark", "landmark",
                    "shopping_mall", "market", "amusement_park", "aquarium", "zoo", "bridge",
                    "scenic_spot", "national_park", "botanical_garden", "amusement_center"}

WARD_PATTERN = re.compile(r"東京都(\S+?区)")


def ward_of(address: str, fallback: str) -> str:
    """주소의 東京都○○区. 없으면 검색한 구"""
    if address:
        m = WARD_PATTERN.search(address)
        if m:
            return m.group(1)
    return fallback


def is_attraction(types: list) -> bool:
    type_set = set(types)
    if type_set & ATTRACTION_TYPES:
        return True
    return not (type_set & EXCLUDE_TYPES)


def search(query: str, fallback_ward: str) -> list:
    """한 검색어로 최대 MAX_PAGES 페이지까지"""
    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": API_KEY,
        "X-Goog-FieldMask": FIELD_MASK,
    }
    body = {"textQuery": query, "languageCode": "ja", "pageSize": PAGE_SIZE}

    results = []
    for page in range(MAX_PAGES):
        try:
            resp = requests.post(SEARCH_URL, headers=headers, json=body, timeout=15)
            data = resp.json()
        except Exception as e:
            print(f"  [오류] {query} p{page + 1} -> {e}")
            break

        if "places" not in data:
            if page == 0:
                print(f"  [결과 없음] {query} -> {data}")
            break

        for place in data["places"]:
            types = place.get("types", [])
            if not is_attraction(types):
                continue
            address = place.get("formattedAddress")
            results.append({
                "ward": ward_of(address, fallback_ward),
                "place_id": place.get("id"),
                "name": place.get("displayName", {}).get("text"),
                "address": address,
                "latitude": place.get("location", {}).get("latitude"),
                "longitude": place.get("location", {}).get("longitude"),
                "rating": place.get("rating"),
                "review_cnt": place.get("userRatingCount"),
                "types": ",".join(types),
            })

        token = data.get("nextPageToken")
        if not token:
            break
        body = {"textQuery": query, "languageCode": "ja", "pageSize": PAGE_SIZE, "pageToken": token}
        time.sleep(PAGE_SLEEP_SEC)

    return results


def main():
    if not API_KEY:
        raise RuntimeError("GOOGLE_MAPS_API_KEY 환경변수가 설정되지 않았습니다.")

    jobs = [(f"{ward} 東京 {q}", ward) for ward in WARDS for q in WARD_QUERIES]
    jobs += [(q, "") for q in AREA_QUERIES]

    all_results = []
    for query, ward in jobs:
        rows = search(query, ward)
        print(f"[검색] {query} -> {len(rows)}건")
        all_results.extend(rows)
        time.sleep(SLEEP_SEC)

    df = pd.DataFrame(all_results)
    if os.path.exists(OLD_CSV):
        old = pd.read_csv(OLD_CSV, encoding="utf-8-sig")
        print(f"[기존 CSV] {len(old)}건 합침 (같은 place_id는 새 값 우선)")
        df = pd.concat([df, old], ignore_index=True)
    before = len(df)
    df = df.drop_duplicates(subset=["place_id"])
    # 도쿄 23구 밖(주소에 区가 없음) 제외
    df = df[df["ward"].str.endswith("区", na=False)]
    print(f"[중복·범위 정리] {before}건 -> {len(df)}건")
    print(df["ward"].value_counts().to_string())
    print(f"리뷰 100개 이상: {(df['review_cnt'].fillna(0) >= 100).sum()}건")

    # 서버 임포터가 읽는 컬럼 순서 그대로
    df = df[["ward", "place_id", "name", "address", "latitude", "longitude", "rating", "review_cnt", "types"]]
    df.to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")
    print(f"[완료] 저장: {OUTPUT_CSV} ({len(df)}건)")


if __name__ == "__main__":
    main()
