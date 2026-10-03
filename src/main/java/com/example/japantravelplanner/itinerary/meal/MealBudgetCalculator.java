package com.example.japantravelplanner.itinerary.meal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전체 여행 예산(원) → 1인 끼니당 상한(엔)
 * 1인 하루 예산 × 식비 비율 → 엔화 → 점심 35% / 저녁 65%, 100엔 단위로 내림
 */
@Component
@RequiredArgsConstructor
public class MealBudgetCalculator {

    private final MealProperties props;

    public MealBudget calculate(int totalBudgetKrw, int travelers, int days, double foodRatio) {
        double perPersonPerDayKrw = (double) totalBudgetKrw / Math.max(1, travelers) / Math.max(1, days);
        double foodYen = perPersonPerDayKrw * foodRatio / props.getKrwPerYen();

        int lunch = floorTo100(foodYen * props.getLunchShare());
        int dinner = floorTo100(foodYen * (1 - props.getLunchShare()));
        return new MealBudget(
                Math.max(lunch, props.getMinLunchYen()),
                Math.max(dinner, props.getMinDinnerYen()));
    }

    private int floorTo100(double yen) {
        return (int) (Math.floor(yen / 100) * 100);
    }
}