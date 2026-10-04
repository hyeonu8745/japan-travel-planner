package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 여행 전체에서 지금까지 고른 식당 기록. 일정 하나를 만드는 동안만 쓰고 버린다 (Bean 아님)
 * - 이미 고른 식당은 다시 고르지 않음
 * - 바로 앞 끼니의 장르 (전날 저녁 → 다음 날 점심도 이어짐)
 */
public class MealHistory {

    private final Set<Long> usedIds = new HashSet<>();
    private Set<String> lastGenres = Set.of();

    public void record(RestaurantCandidate chosen) {
        usedIds.add(chosen.restaurant().getId());
        lastGenres = RestaurantFinder.genresOf(chosen.restaurant().getGenre());
    }

    public Set<Long> usedIds() {
        return Collections.unmodifiableSet(usedIds);
    }

    public Set<String> lastGenres() {
        return lastGenres;
    }
}