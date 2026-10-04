package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.meal.GenreGroups;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 여행 전체에서 지금까지 고른 식당 기록. 일정 하나를 만드는 동안만 쓰고 버린다 (Bean 아님)
 * - 이미 고른 식당은 다시 고르지 않음
 * - 바로 앞 끼니의 장르 (전날 저녁 → 다음 날 점심도 이어짐)
 * - 비슷한 음식 묶음(라멘류 등)을 지금까지 몇 번 골랐는지
 */
public class MealHistory {

    private final Set<Long> usedIds = new HashSet<>();
    private final Map<String, Integer> groupCounts = new HashMap<>();
    private Set<String> lastGenres = Set.of();

    public void record(RestaurantCandidate chosen) {
        usedIds.add(chosen.restaurant().getId());
        lastGenres = RestaurantFinder.genresOf(chosen.restaurant().getGenre());
        for (String group : GenreGroups.groupsOf(lastGenres)) {
            groupCounts.merge(group, 1, Integer::sum);
        }
    }

    /** 이 장르들이 속한 묶음 중 가장 많이 나온 횟수 (한 번도 안 나왔으면 0) */
    public int repeatCount(Set<String> genres) {
        return GenreGroups.groupsOf(genres).stream()
                .mapToInt(group -> groupCounts.getOrDefault(group, 0))
                .max()
                .orElse(0);
    }

    public Set<Long> usedIds() {
        return Collections.unmodifiableSet(usedIds);
    }

    public Set<String> lastGenres() {
        return lastGenres;
    }
}