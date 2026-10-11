package com.example.japantravelplanner.itinerary.zone;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 좌표를 가장 가까운 지역에 배정한다.
 * 어느 지역 중심에서도 3km를 넘으면 자동 일정 후보에서 제외하기 위해 빈 값을 돌려준다.
 */
@Component
public class ZoneAssigner {

    static final double MAX_DISTANCE_M = TripZone.MAX_DISTANCE_M;

    public Optional<TripZone> assign(double lat, double lng) {
        return TripZone.nearest(lat, lng);
    }
}
