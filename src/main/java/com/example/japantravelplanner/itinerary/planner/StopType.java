package com.example.japantravelplanner.itinerary.planner;

/**
 * 일정에 들어가는 항목의 종류. 야경 장소도 ATTRACTION (시각으로 구분).
 * CAFE (10-16): 오후 쉬는 시간의 카페·디저트 (식당 데이터, DB CHECK 제약 SQL 필요 — 인수인계 6장)
 */
public enum StopType {
    ATTRACTION,
    LUNCH,
    DINNER,
    CAFE
}