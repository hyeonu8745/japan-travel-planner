package com.example.japantravelplanner.itinerary.planner;

/** 하루의 점심·저녁. 시간대 때문에 넣지 않는 끼니는 null */
public record DayMeals(PlannedMeal lunch, PlannedMeal dinner) {
}