package com.example.japantravelplanner.itinerary.meal;

import java.util.List;
import java.util.Set;

/**
 * 식당을 고를 때의 음식 조건
 * @param preferGenres        좋아하는 장르 (가산점)
 * @param avoidGenres         피할 장르 (제외)
 * @param avoidSmoky          흡연 가능성 있는 장르 제외 (이자카야·바)
 * @param hasAllergies        조건 입력에 알레르기가 적혀 있음 (10-16: 일정 요약에 "원재료는 주문할 때 확인" 안내)
 * @param unfilteredAllergies 식당 장르로는 거를 수 없는 알레르기 재료 (땅콩·우유·계란 등, 요약에서 이름을 들어 안내)
 */
public record FoodFilter(Set<String> preferGenres, Set<String> avoidGenres, boolean avoidSmoky,
                         boolean hasAllergies, List<String> unfilteredAllergies) {

    public FoodFilter {
        unfilteredAllergies = unfilteredAllergies == null ? List.of() : List.copyOf(unfilteredAllergies);
    }

    public FoodFilter(Set<String> preferGenres, Set<String> avoidGenres, boolean avoidSmoky) {
        this(preferGenres, avoidGenres, avoidSmoky, false, List.of());
    }

    /** 이 장르의 식당을 후보에서 뺄지 (피할 장르가 하나라도 있거나, 담배를 피하는데 흡연 장르) */
    public boolean excludes(Set<String> genres) {
        if (genres.stream().anyMatch(avoidGenres::contains)) return true;
        return avoidSmoky && genres.stream().anyMatch(RestaurantFinder::isSmokyGenre);
    }
}
