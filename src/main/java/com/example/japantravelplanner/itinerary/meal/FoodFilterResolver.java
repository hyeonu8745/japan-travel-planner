package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.itinerary.dictionary.FoodDictionary;
import com.example.japantravelplanner.itinerary.dictionary.FoodPreferenceType;
import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 정리된 취향 + 조건 입력의 알레르기 → 식당 음식 조건
 * 좋아하는지 피하는지는 AI가 붙인 카테고리로 구분하고, 사전은 단어를 장르로 바꾸기만 한다
 */
@Component
@RequiredArgsConstructor
public class FoodFilterResolver {

    private final FoodDictionary dictionary;

    public FoodFilter resolve(List<PreferenceConstraint> constraints, String allergies) {
        Set<String> prefer = new LinkedHashSet<>();
        Set<String> avoid = new LinkedHashSet<>();
        boolean avoidSmoky = false;

        if (constraints != null) {
            for (PreferenceConstraint c : constraints) {
                switch (c.getCategory()) {
                    case FOOD_PREFER -> prefer.addAll(dictionary.findGenres(c.getDescription(), FoodPreferenceType.PREFER));
                    case FOOD_AVOID -> avoid.addAll(dictionary.findGenres(c.getDescription(), FoodPreferenceType.AVOID));
                    case SMOKING -> avoidSmoky = true;
                    default -> { }
                }
            }
        }
        // 조건 입력 화면의 알레르기도 피할 음식으로
        avoid.addAll(dictionary.findGenres(allergies, FoodPreferenceType.AVOID));

        // 피할 장르가 좋아하는 장르보다 우선
        prefer.removeAll(avoid);
        boolean hasAllergies = allergies != null && !allergies.isBlank();
        return new FoodFilter(prefer, avoid, avoidSmoky, hasAllergies, AllergyWords.unfilterable(allergies));
    }
}