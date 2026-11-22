package com.example.japantravelplanner.trip.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 여행 속도. 하루에 방문할 관광지 수(오전/오후)와 체류 시간 배율을 가진다.
 */
@Getter
@RequiredArgsConstructor
public enum TravelPace {

    RELAXED(1, 2, 1.3),   // 여유롭게: 하루 3곳, 체류 시간 1.3배
    NORMAL(2, 2, 1.0),    // 보통: 하루 4곳
    FULL(2, 3, 0.85);     // 알차게: 하루 5곳, 체류 시간 0.85배

    private final int morningCount;
    private final int afternoonCount;
    private final double stayMultiplier;

    /** 하루에 방문할 관광지 수 (오전 + 오후). 11-22부터 저녁 뒤 야경 장소도 이 수에 들어간다 */
    public int attractionsPerDay() {
        return morningCount + afternoonCount;
    }

    /**
     * 당일치기 날 관광지 수 (11-22 사용자 경험: 숙소에서 9시에 나서 점심 먹고 걸어 다니면 많아야 3곳,
     * 돌아와서 저녁 먹는 시간까지 생각하면 알차게도 그 이상은 못 본다). 여유롭게 2곳, 보통·알차게 3곳
     */
    public int dayTripAttractions() {
        return Math.min(attractionsPerDay(), this == RELAXED ? 2 : 3);
    }
}