package com.example.japantravelplanner.itinerary.travel;

/**
 * 한 구간의 이동 정보
 * @param estimated true면 Google 조회가 실패해서 대신 계산한 값 (화면에 "예상치" 배지)
 *                  자체 계산이 기본인 지금은 false. 화면 상단에 "이동 시간은 예상치" 안내를 한 번만 표시
 */
public record TravelLeg(int minutes, int distanceMeters, LegMode mode, boolean estimated) {
}