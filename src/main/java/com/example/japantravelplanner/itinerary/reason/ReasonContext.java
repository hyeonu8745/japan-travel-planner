package com.example.japantravelplanner.itinerary.reason;

import com.example.japantravelplanner.itinerary.candidate.Wish;
import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.trip.entity.Interest;

import java.util.List;
import java.util.Set;

/**
 * 이유를 계산할 때 필요한 여행 조건 (일정을 만들 때와 같은 기준)
 * @param interests 조건 입력의 관심사
 * @param food      대화에서 정리한 취향 + 알레르기로 만든 음식 조건 (FoodFilterResolver)
 * @param wishes    대화에서 말한, 관심사 체크박스에 없는 취향 (피규어 숍 등)
 */
public record ReasonContext(List<Interest> interests, FoodFilter food, Set<Wish> wishes) {

    /** 여행 조건을 모를 때 (관심사·좋아하는 음식 이유는 붙지 않는다) */
    public static final ReasonContext NONE = new ReasonContext(List.of(), null);

    public ReasonContext {
        interests = interests == null ? List.of() : List.copyOf(interests);
        food = food == null ? new FoodFilter(Set.of(), Set.of(), false) : food;
        wishes = wishes == null ? Set.of() : Set.copyOf(wishes);
    }

    public ReasonContext(List<Interest> interests, FoodFilter food) {
        this(interests, food, Set.of());
    }

    public static ReasonContext orNone(ReasonContext context) {
        return context == null ? NONE : context;
    }
}
