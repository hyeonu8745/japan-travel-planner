package com.example.japantravelplanner.itinerary.budget;

import com.example.japantravelplanner.itinerary.planner.StopType;

import java.time.LocalDate;
import java.util.List;

/**
 * 일정의 식비 요약 (10-12). 타베로그 끼니 예산 범위로 계산한 대략적인 금액이며 교통비·숙박비·입장료·쇼핑은 포함하지 않는다.
 *
 * @param travelers        인원 (식비 합계는 인원 전체)
 * @param krwPerYen        이 일정 버전의 환율 (1엔당 원)
 * @param tripBudgetKrw    여행 전체 예산 (원)
 * @param foodRatio        식비 비율 (대화에서 정한 값, 기본 0.4)
 * @param foodBudgetKrw    식비 예산 = 전체 예산 × 식비 비율
 * @param food             일정의 식비 합계 (인원 전체)
 * @param perPerson        1인 식비 합계
 * @param verdict          WITHIN(최대로 잡아도 식비 예산 안) / MAYBE_OVER(범위가 예산에 걸침) / OVER(최소로 잡아도 넘음) / UNKNOWN(가격 정보 없음)
 * @param tripBudgetShare  식비 최대 금액이 전체 예산에서 차지하는 비율 (%)
 * @param note             화면에 그대로 보여줄 안내
 */
public record BudgetSummaryDto(Long tripId, int version, int travelers, double krwPerYen, LocalDate exchangeRateDate,
                               String exchangeRateSourceLabel, int tripBudgetKrw, double foodRatio, int foodBudgetKrw,
                               Amount food, Amount perPerson, String verdict, int tripBudgetShare, String note,
                               List<DayDto> days) {

    /** 금액 범위 (엔·원). unknownMeals: 가격 정보가 없어 빠진 끼니 수 */
    public record Amount(int minYen, int maxYen, int minKrw, int maxKrw, int meals, int unknownMeals) {
    }

    /** 하루 식비 (인원 전체) */
    public record DayDto(int dayNumber, LocalDate date, int minYen, int maxYen, int minKrw, int maxKrw,
                         List<MealDto> meals) {
    }

    /**
     * 끼니 하나 (1인 기준).
     *
     * @param budgetText 타베로그 원래 표기 ("￥1,000～￥1,999")
     * @param minYen     null이면 가격 정보 없음
     */
    public record MealDto(StopType type, String name, String nameKo, String budgetText, Integer minYen, Integer maxYen) {
    }

    public static final String NOTE = "교통비·숙박비·관광지 입장료·쇼핑과 일정에 없는 아침·간식은 포함하지 않았어요. "
            + "식비는 일정에 들어간 점심·저녁 식당의 1인 예산 범위(타베로그)로 계산한 대략적인 금액이에요.";
}
