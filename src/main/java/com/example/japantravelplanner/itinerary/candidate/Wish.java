package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 조건 입력의 관심사 체크박스에는 없지만 대화에서 말한 취향 (ETC·SCHEDULE·ATMOSPHERE 조건의 낱말로 알아본다).
 * 관심사처럼 맞는 관광지에 가산점을 주고(AttractionScorer), 고른 이유 칩·요약에도 쓴다.
 * 10-10 화면에서 발견: "피규어 샵 가고 싶어요"라고 했는데 관심사 체크가 없어 일정에 반영되지 않았다.
 * 데이터에는 아키하바라 피규어·하비 숍(toy_store)이 수십 곳 있다.
 */
public enum Wish {

    HOBBY("피규어·애니",
            List.of("피규어", "프라모델", "건프라", "애니", "굿즈", "만화", "장난감", "캐릭터", "가챠", "오타쿠", "하비"),
            Set.of("toy_store"));

    private final String label;
    private final List<String> keywords;
    private final Set<String> types;

    Wish(String label, List<String> keywords, Set<String> types) {
        this.label = label;
        this.keywords = keywords;
        this.types = types;
    }

    public String label() {
        return label;
    }

    /** 대화에서 정리한 조건에서 취향을 찾는다 (음식·흡연·이동 조건은 보지 않는다) */
    public static Set<Wish> from(Collection<PreferenceConstraint> constraints) {
        Set<Wish> result = EnumSet.noneOf(Wish.class);
        if (constraints == null) return result;
        for (PreferenceConstraint c : constraints) {
            if (c.getCategory() != ConstraintCategory.ETC && c.getCategory() != ConstraintCategory.SCHEDULE
                    && c.getCategory() != ConstraintCategory.ATMOSPHERE) continue;
            String text = c.getDescription() == null ? "" : c.getDescription().replace(" ", "");
            for (Wish wish : values()) {
                if (wish.keywords.stream().anyMatch(text::contains)) result.add(wish);
            }
        }
        return result;
    }

    public boolean matches(Attraction attraction) {
        if (attraction == null || attraction.getTypes() == null) return false;
        Set<String> attractionTypes = Arrays.stream(attraction.getTypes().split(","))
                .map(String::trim).collect(Collectors.toSet());
        return types.stream().anyMatch(attractionTypes::contains);
    }
}
