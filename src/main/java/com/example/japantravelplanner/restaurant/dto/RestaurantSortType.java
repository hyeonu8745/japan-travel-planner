package com.example.japantravelplanner.restaurant.dto;

/**
 * 식당 검색 정렬 (거리순은 위치 조건이 있어야 한다).
 */
public enum RestaurantSortType {
    DISTANCE,   // 거리순 (위치 조건 필수)
    RATING,     // 평점순
    REVIEWS     // 리뷰 수순
}