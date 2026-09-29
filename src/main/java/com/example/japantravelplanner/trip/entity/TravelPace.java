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

    public int attractionsPerDay() {
        return morningCount + afternoonCount;
    }
}