package com.example.japantravelplanner.itinerary.meal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MealBudgetCalculatorTest {

    private final MealBudgetCalculator calculator = new MealBudgetCalculator(new MealProperties());

    @Test
    void 전체_예산을_끼니당_엔화로_나눈다() {
        // 160만 원, 2명, 4일 → 1인 하루 20만 원 → 식비 40% 8만 원 → 약 8,888엔
        MealBudget budget = calculator.calculate(1_600_000, 2, 4, 0.4);
        assertThat(budget.lunchMaxYen()).isEqualTo(3100);    // 8,888 × 35% = 3,111 → 100엔 내림
        assertThat(budget.dinnerMaxYen()).isEqualTo(5700);   // 8,888 × 65% = 5,777
    }

    @Test
    void 예산이_아주_적어도_최소값은_보장한다() {
        MealBudget budget = calculator.calculate(100_000, 2, 4, 0.4);
        assertThat(budget.lunchMaxYen()).isEqualTo(1000);
        assertThat(budget.dinnerMaxYen()).isEqualTo(2000);
    }

    @Test
    void 음식에_더_쓰고_싶다고_하면_상한이_올라간다() {
        MealBudget normal = calculator.calculate(1_600_000, 2, 4, 0.4);
        MealBudget foodie = calculator.calculate(1_600_000, 2, 4, 0.55);
        assertThat(foodie.dinnerMaxYen()).isGreaterThan(normal.dinnerMaxYen());
    }

    @Test
    void 환율을_넘기면_그_환율로_엔화를_계산한다() {
        // 10-12: 환율 API. 1엔 10원이면 8만 원 → 8,000엔
        MealBudget budget = calculator.calculate(1_600_000, 2, 4, 0.4, 10.0);
        assertThat(budget.lunchMaxYen()).isEqualTo(2800);    // 8,000 × 35%
        assertThat(budget.dinnerMaxYen()).isEqualTo(5200);   // 8,000 × 65%
        assertThat(calculator.calculate(1_600_000, 2, 4, 0.4, 0)).isEqualTo(calculator.calculate(1_600_000, 2, 4, 0.4));
    }
}
