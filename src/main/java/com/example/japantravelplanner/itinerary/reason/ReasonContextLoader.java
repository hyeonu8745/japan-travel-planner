package com.example.japantravelplanner.itinerary.reason;

import com.example.japantravelplanner.itinerary.meal.FoodFilterResolver;
import com.example.japantravelplanner.itinerary.service.ItineraryPlanner;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 여행의 관심사와 음식 조건을 읽어서 이유 계산에 넘긴다. 트랜잭션 안에서 부른다 (관심사 목록이 지연 로딩) */
@Component
@RequiredArgsConstructor
public class ReasonContextLoader {

    private final TripPreferenceRepository preferenceRepository;
    private final FoodFilterResolver foodFilterResolver;

    public ReasonContext load(Trip trip) {
        if (trip == null) return ReasonContext.NONE;
        TripPreference preference = preferenceRepository.findByTripId(trip.getId()).orElse(null);
        return new ReasonContext(ItineraryPlanner.interestsOf(trip),
                foodFilterResolver.resolve(ItineraryPlanner.constraintsOf(preference), trip.getAllergies()));
    }
}
