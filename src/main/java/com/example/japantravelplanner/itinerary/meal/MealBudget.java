package com.example.japantravelplanner.itinerary.meal;

/** 1인 끼니당 예산 상한 (엔) */
public record MealBudget(int lunchMaxYen, int dinnerMaxYen) {

    public int of(Meal meal) {
        return meal == Meal.LUNCH ? lunchMaxYen : dinnerMaxYen;
    }
}