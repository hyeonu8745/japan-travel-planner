package com.example.japantravelplanner.itinerary.meal;

/** 1인 끼니당 예산 상한 (엔) */
public record MealBudget(int lunchMaxYen, int dinnerMaxYen) {

    /** 끼니의 1인 상한 (점심이면 점심, 저녁·카페는 저녁) */
    public int of(Meal meal) {
        return meal == Meal.LUNCH ? lunchMaxYen : dinnerMaxYen;
    }
}