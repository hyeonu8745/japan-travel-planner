package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.meal.*;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 정해진 관광지 순서 사이에 점심·저녁 식당을 넣는다.
 * - 점심: 오전 마지막 관광지와 오후 첫 관광지의 중간 지점 근처
 * - 저녁: 오후 마지막 관광지 근처. 저녁 후 야경 장소가 있으면 그곳과의 중간 지점
 * - 같은 묶음(라멘류 등)이 이미 나왔으면 점수를 깎아서 다른 음식이 위로 (여행 전체 기준)
 * - 바로 앞 끼니와 장르가 겹치면 다음 후보 (라멘 → 라멘 방지)
 * - 여행 전체에서 같은 식당은 한 번만
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MealPlanner {

    static final int SEARCH_LIMIT = 15;     // 감점·장르 겹침으로 순위가 바뀔 때 넘겨볼 여유
    static final int ALTERNATIVES = 2;

    private final RestaurantFinder restaurantFinder;
    private final MealProperties props;

    public DayMeals plan(DayRoute route, TravelPoint hotel, DayWindow window,
                         MealBudget budget, FoodFilter filter, MealHistory history) {
        PlannedMeal lunch = window.lunch()
                ? pick(Meal.LUNCH, lunchAnchor(route, hotel), budget, filter, history)
                : null;
        PlannedMeal dinner = window.dinner()
                ? pick(Meal.DINNER, dinnerAnchor(route, hotel), budget, filter, history)
                : null;

        log.info("[Meal] DAY{} 점심 {} / 저녁 {}", route.day().dayNumber(), label(lunch), label(dinner));
        return new DayMeals(lunch, dinner);
    }

    private PlannedMeal pick(Meal meal, TravelPoint anchor, MealBudget budget,
                             FoodFilter filter, MealHistory history) {
        List<RestaurantCandidate> ranked = restaurantFinder.find(anchor.lat(), anchor.lng(), meal,
                budget.of(meal), filter, history.usedIds(), SEARCH_LIMIT);
        if (ranked.isEmpty()) {
            return PlannedMeal.notFound(meal, anchor);
        }

        List<RestaurantCandidate> reranked = rerank(ranked, history, props.getRepeatGroupPenalty());
        PlannedMeal planned = chooseAvoidingRepeat(meal, anchor, reranked, history.lastGenres());
        history.record(planned.chosen());
        return planned;
    }

    /**
     * 이미 나온 묶음(라멘류 등)의 식당은 나온 횟수 × penalty 만큼 점수를 깎아서 다시 정렬한다.
     * 점수가 같으면 원래 순서 유지
     */
    static List<RestaurantCandidate> rerank(List<RestaurantCandidate> ranked, MealHistory history, double penalty) {
        List<RestaurantCandidate> sorted = new ArrayList<>(ranked);
        sorted.sort(Comparator.comparingDouble((RestaurantCandidate c) ->
                        c.score() - penalty * history.repeatCount(RestaurantFinder.genresOf(c.restaurant().getGenre())))
                .reversed());
        return sorted;
    }

    /**
     * 점수순 후보에서 앞 끼니와 장르가 하나도 겹치지 않는 첫 식당을 고른다.
     * 모두 겹치면 1위를 고르고 genreRepeated = true
     */
    static PlannedMeal chooseAvoidingRepeat(Meal meal, TravelPoint anchor,
                                            List<RestaurantCandidate> ranked, Set<String> lastGenres) {
        int chosenIndex = -1;
        for (int i = 0; i < ranked.size(); i++) {
            Set<String> genres = RestaurantFinder.genresOf(ranked.get(i).restaurant().getGenre());
            if (Collections.disjoint(genres, lastGenres)) {
                chosenIndex = i;
                break;
            }
        }
        boolean repeated = chosenIndex < 0;
        if (repeated) chosenIndex = 0;

        List<RestaurantCandidate> alternatives = new ArrayList<>();
        for (int i = 0; i < ranked.size() && alternatives.size() < ALTERNATIVES; i++) {
            if (i != chosenIndex) alternatives.add(ranked.get(i));
        }
        return new PlannedMeal(meal, anchor, ranked.get(chosenIndex), List.copyOf(alternatives), repeated);
    }

    /** 오전 마지막 ↔ 오후 첫 관광지의 중간. 한쪽만 있으면 그곳, 둘 다 없으면 숙소 */
    static TravelPoint lunchAnchor(DayRoute route, TravelPoint hotel) {
        ScoredAttraction before = last(route.morning());
        ScoredAttraction after = route.afternoon().isEmpty() ? null : route.afternoon().get(0);

        if (before != null && after != null) return midpoint("점심 기준(중간)", before, after);
        if (before != null) return point("점심 기준", before);
        if (after != null) return point("점심 기준", after);
        return hotel;
    }

    /** 낮 마지막 관광지 근처. 야경 장소가 있으면 그 사이 중간 */
    static TravelPoint dinnerAnchor(DayRoute route, TravelPoint hotel) {
        ScoredAttraction lastDay = last(route.daytime());
        ScoredAttraction night = route.night();

        if (lastDay != null && night != null) return midpoint("저녁 기준(중간)", lastDay, night);
        if (lastDay != null) return point("저녁 기준", lastDay);
        if (night != null) return point("저녁 기준", night);
        return hotel;
    }

    /** 몇 km 안의 두 점이라 위도·경도 평균으로 충분 */
    private static TravelPoint midpoint(String name, ScoredAttraction a, ScoredAttraction b) {
        return new TravelPoint(name, (a.lat() + b.lat()) / 2, (a.lng() + b.lng()) / 2);
    }

    private static TravelPoint point(String name, ScoredAttraction a) {
        return new TravelPoint(name, a.lat(), a.lng());
    }

    private static ScoredAttraction last(List<ScoredAttraction> list) {
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private static String label(PlannedMeal meal) {
        if (meal == null) return "-";
        if (!meal.found()) return "후보 없음";
        return meal.chosen().restaurant().getName() + "(" + meal.chosen().restaurant().getGenre() + ")"
                + (meal.genreRepeated() ? " 장르 중복" : "");
    }
}