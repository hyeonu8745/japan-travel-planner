package com.example.japantravelplanner.itinerary.meal;

import java.util.Set;

/**
 * 식당을 고를 때의 음식 조건
 * @param preferGenres 좋아하는 장르 (가산점)
 * @param avoidGenres  피할 장르 (제외)
 * @param avoidSmoky   흡연 가능성 있는 장르 제외 (이자카야·바)
 */
public record FoodFilter(Set<String> preferGenres, Set<String> avoidGenres, boolean avoidSmoky) {
}