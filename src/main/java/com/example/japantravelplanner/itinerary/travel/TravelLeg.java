package com.example.japantravelplanner.itinerary.travel;

/**
 * 한 구간의 이동 정보
 * @param estimated true면 API가 아니라 직선거리로 추정한 값 (화면에 "예상치" 표시)
 */
public record TravelLeg(int minutes, int distanceMeters, LegMode mode, boolean estimated) {
}