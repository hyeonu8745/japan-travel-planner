package com.example.japantravelplanner.itinerary.travel;

import java.util.List;

/** points[i] → points[j] 의 이동 정보 표. 자기 자신(i == j)은 0분 */
public record TravelMatrix(List<TravelPoint> points, TravelLeg[][] legs) {

    public TravelLeg get(int from, int to) {
        return legs[from][to];
    }

    public int minutes(int from, int to) {
        return from == to ? 0 : legs[from][to].minutes();
    }
}