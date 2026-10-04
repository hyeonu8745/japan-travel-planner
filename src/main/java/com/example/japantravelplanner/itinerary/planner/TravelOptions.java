package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.entity.Trip;

import java.util.List;

/**
 * 여행 하나의 이동 방식. 동선·끼니·시간 계산이 모두 같은 값을 쓰도록 한 곳에서 정한다.
 * @param mode                 기본 이동수단 (대중교통 또는 렌트카)
 * @param walkThresholdMinutes 이 시간 이내는 걸어감. 여유 속도이거나 이동 제약이 있으면 짧게
 */
public record TravelOptions(LegMode mode, int walkThresholdMinutes) {

    public static TravelOptions of(Trip trip, List<PreferenceConstraint> constraints, TravelProperties props) {
        LegMode mode = trip.getTransportMode() == TransportMode.RENTAL_CAR ? LegMode.DRIVE : LegMode.TRANSIT;

        boolean gentle = trip.getTravelPace() == TravelPace.RELAXED
                || (trip.getMobilityConstraints() != null && !trip.getMobilityConstraints().isBlank())
                || (constraints != null && constraints.stream()
                .anyMatch(c -> c.getCategory() == ConstraintCategory.MOBILITY));

        int threshold = gentle ? props.getRelaxedWalkThresholdMinutes() : props.getWalkThresholdMinutes();
        return new TravelOptions(mode, threshold);
    }
}