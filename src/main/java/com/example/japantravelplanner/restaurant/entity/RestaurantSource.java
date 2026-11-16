package com.example.japantravelplanner.restaurant.entity;

/**
 * 식당 데이터의 출처. 도쿄는 캐글 타베로그 데이터셋, 다른 도시는 따로 받은 타베로그 데이터, 체인점은 OpenStreetMap 브랜드 태그.
 * 출처마다 있는 칸이 다르다 (체인점은 평점이 없고 예산은 브랜드별 값).
 */
public enum RestaurantSource {
    KAGGLE,
    TABELOG,
    CHAIN;

    /** 체인점(OSM 브랜드)인지 */
    public boolean isChain() {
        return this == CHAIN;
    }
}
