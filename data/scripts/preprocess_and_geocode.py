# -*- coding: utf-8 -*-
"""
도쿄 타베로그 캐글 데이터 정제 + Google Geocoding 스크립트

사용법:
    1) 아래 CONFIG 영역을 본인 환경에 맞게 수정
    2) 터미널에서 실행 전에 API 키를 환경변수로 등록
       (Windows PowerShell 예시)
       $env:GOOGLE_MAPS_API_KEY="여기에_실제_키"
    3) python preprocess_and_geocode.py 실행

동작 방식:
    - 원본 CSV를 읽어 관광지 중심 6개 구 + 평점 3.2 이상으로 필터링
    - genre 컬럼 정제 (뒤쪽 공백 제거, 구분자를 콤마로 통일)
    - 중복 주소는 한 번만 Geocoding 호출 (비용 절감)
    - 중간에 끊겨도 이어서 할 수 있도록 캐시 파일(geocode_cache.csv)에 진행상황 저장
    - 최종 결과를 output CSV로 저장 (Spring Boot에서 그대로 임포트 가능한 컬럼 구성)
"""

import os
import time
import json
import pandas as pd
import requests

# =========================
# CONFIG - 여기만 수정하면 됨
# =========================
INPUT_CSV = "Tokyo_Restaurant_Reviews_Tabelog.csv"      # 원본 캐글 CSV 경로
OUTPUT_CSV = "restaurants_tokyo_filtered_geocoded.csv"  # 최종 결과 저장 경로
CACHE_CSV = "geocode_cache.csv"                         # 주소별 위경도 캐시 (중단 후 재시작용)

TARGET_WARDS = ["台東区", "渋谷区", "新宿区", "港区", "中央区", "千代田区"]
MIN_RATING = 3.4  # Google Geocoding 무료 한도(월 10,000건) 내로 맞추기 위해 3.4로 설정
                  # (3.4 이상 기준 고유주소 약 8,560건 -> 무료 한도 내 안전)

API_KEY = os.environ.get("GOOGLE_MAPS_API_KEY", "")
GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json"

SLEEP_SEC = 0.05      # 요청 사이 대기시간 (초당 과도한 호출 방지)
SAVE_EVERY = 200      # 몇 건마다 캐시 파일에 중간 저장할지


def load_and_filter(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)

    before = len(df)
    df = df[df["municipalities_1"].isin(TARGET_WARDS)]
    df = df[df["rating_val"] >= MIN_RATING]
    after = len(df)
    print(f"[필터링] {before}건 -> {after}건 (구 6개 + 평점 {MIN_RATING} 이상)")

    return df.reset_index(drop=True)


def clean_genre(genre: str) -> str:
    if pd.isna(genre):
        return ""
    # 뒤쪽 공백/개행 제거 + 일본식 구분자(、)를 콤마로 통일
    cleaned = genre.strip()
    parts = [p.strip() for p in cleaned.split("、") if p.strip()]
    return ",".join(parts)


def load_cache() -> dict:
    if os.path.exists(CACHE_CSV):
        cache_df = pd.read_csv(CACHE_CSV)
        return {
            row["address"]: (row["lat"], row["lng"])
            for _, row in cache_df.iterrows()
            if pd.notna(row["lat"]) and pd.notna(row["lng"])
        }
    return {}


def save_cache(cache: dict):
    rows = [{"address": addr, "lat": v[0], "lng": v[1]} for addr, v in cache.items()]
    pd.DataFrame(rows).to_csv(CACHE_CSV, index=False, encoding="utf-8-sig")


def geocode_address(address: str) -> tuple:
    """Google Geocoding API 호출, 실패 시 (None, None) 반환"""
    try:
        resp = requests.get(
            GEOCODE_URL,
            params={"address": address, "key": API_KEY},
            timeout=10,
        )
        data = resp.json()
        if data.get("status") == "OK" and data.get("results"):
            loc = data["results"][0]["geometry"]["location"]
            return loc["lat"], loc["lng"]
        else:
            print(f"  [실패] {address} -> status={data.get('status')}")
            return None, None
    except Exception as e:
        print(f"  [오류] {address} -> {e}")
        return None, None


def geocode_all(df: pd.DataFrame) -> pd.DataFrame:
    if not API_KEY:
        raise RuntimeError(
            "GOOGLE_MAPS_API_KEY 환경변수가 설정되지 않았습니다. "
            "실행 전에 API 키를 환경변수로 등록해주세요."
        )

    unique_addresses = df["address"].dropna().unique().tolist()
    print(f"[지오코딩] 고유 주소 {len(unique_addresses)}건 (전체 {len(df)}건 중 중복 제거)")

    cache = load_cache()
    print(f"[캐시] 기존 캐시에서 {len(cache)}건 재사용 가능")

    todo = [addr for addr in unique_addresses if addr not in cache]
    print(f"[지오코딩] 신규로 호출해야 할 주소: {len(todo)}건")

    for i, address in enumerate(todo, start=1):
        lat, lng = geocode_address(address)
        cache[address] = (lat, lng)

        if i % 50 == 0 or i == len(todo):
            print(f"  진행 {i}/{len(todo)}")

        if i % SAVE_EVERY == 0:
            save_cache(cache)

        time.sleep(SLEEP_SEC)

    save_cache(cache)  # 최종 저장

    df["latitude"] = df["address"].map(lambda a: cache.get(a, (None, None))[0])
    df["longitude"] = df["address"].map(lambda a: cache.get(a, (None, None))[1])

    success = df["latitude"].notna().sum()
    print(f"[지오코딩 완료] 성공 {success}/{len(df)}건")

    return df


def main():
    df = load_and_filter(INPUT_CSV)

    df["genre"] = df["genre"].apply(clean_genre)

    df = geocode_all(df)

    # 최종 컬럼 정리 (Spring Boot Restaurant 엔티티 필드와 맞춤)
    final_columns = [
        "store_id", "name", "nearest_station", "nearest_distance",
        "genre", "rating_val", "review_cnt", "save_cnt",
        "budget_dinner", "budget_lunch", "holiday", "address",
        "prefecture", "municipalities_1", "municipalities_2", "municipalities_3",
        "latitude", "longitude",
    ]
    df = df[final_columns]

    df.to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")
    print(f"[완료] 최종 결과 저장: {OUTPUT_CSV} ({len(df)}건)")

    no_geo = df["latitude"].isna().sum()
    if no_geo > 0:
        print(f"[참고] 위경도 변환 실패 {no_geo}건은 latitude/longitude가 비어있습니다. "
              f"필요시 스크립트를 재실행하면 캐시를 활용해 이어서 처리됩니다.")


if __name__ == "__main__":
    main()
