package com.example.japantravelplanner.itinerary.travel;

import java.util.List;

/** points[i] → points[j] 의 이동 정보 표. 자기 자신(i == j)은 0분 */
public record TravelMatrix(List<TravelPoint> points, TravelLeg[][] legs) {

    /** from → to 구간 정보 */
    public TravelLeg get(int from, int to) {
        return legs[from][to];
    }

    /** from → to 분 (같은 곳이면 0) */
    public int minutes(int from, int to) {
        return from == to ? 0 : legs[from][to].minutes();
    }
}