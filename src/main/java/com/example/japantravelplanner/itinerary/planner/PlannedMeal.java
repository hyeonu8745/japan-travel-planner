package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;

import java.util.List;

/**
 * 한 끼에 고른 식당
 * @param anchor        식당을 찾은 기준 위치 (점심: 오전·오후 사이 중간 지점, 저녁: 오후 마지막 관광지 근처)
 * @param chosen        고른 식당. 조건에 맞는 곳이 없으면 null (화면에 안내)
 * @param alternatives  다음 후보. 영업 확인(#27)에서 폐업이면 교체, Stop 수정 때 선택지로 사용
 * @param genreRepeated 앞 끼니와 장르가 겹치지 않는 곳이 없어서 겹친 채로 고른 경우
 */
public record PlannedMeal(Meal meal,
                          TravelPoint anchor,
                          RestaurantCandidate chosen,
                          List<RestaurantCandidate> alternatives,
                          boolean genreRepeated) {

    public static PlannedMeal notFound(Meal meal, TravelPoint anchor) {
        return new PlannedMeal(meal, anchor, null, List.of(), false);
    }

    public boolean found() {
        return chosen != null;
    }
}