package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.trip.entity.Interest;

import java.util.Map;
import java.util.Set;

/** 관심사와 어울리는 Google Places type */
public final class InterestTypes {

    private InterestTypes() {
    }

    private static final Map<Interest, Set<String>> TYPES = Map.of(
            Interest.TRADITIONAL, Set.of("buddhist_temple", "shinto_shrine", "place_of_worship",
                    "historical_landmark", "historical_place", "museum", "art_museum"),
            Interest.NIGHT_VIEW, Set.of("observation_deck", "landmark", "bridge"),
            // store, clothing_store 같은 범용 태그는 모스크·박물관 기념품점에도 붙어서 제외
            Interest.SHOPPING, Set.of("shopping_mall", "market", "department_store"),
            // food_store는 식료품점 태그라 관광지 성격과 무관해서 제외
            Interest.FOOD, Set.of("market")
    );

    public static Set<String> of(Interest interest) {
        return TYPES.getOrDefault(interest, Set.of());
    }
}