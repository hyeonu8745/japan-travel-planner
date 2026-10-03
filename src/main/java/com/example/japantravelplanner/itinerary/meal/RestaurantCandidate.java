package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.restaurant.entity.Restaurant;

/**
 * @param budgetRelaxed true면 예산 안에서 못 찾아 상한을 올려서 찾은 식당 (화면에 안내)
 */
public record RestaurantCandidate(Restaurant restaurant, int distanceMeters, double score, boolean budgetRelaxed) {
}