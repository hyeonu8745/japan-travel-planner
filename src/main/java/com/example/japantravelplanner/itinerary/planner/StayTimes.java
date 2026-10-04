package com.example.japantravelplanner.itinerary.planner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 장소 종류별 체류 시간(분). Google type이 여러 개 붙어 있으면 아래 순서에서 먼저 맞는 것을 쓴다.
 * 전망대 > 놀이공원·동물원·수족관 > 박물관·미술관 > 사찰·신사 > 공원·정원 > 쇼핑·시장 > 기타
 * (도쿄타워에 shopping_mall이 같이 붙어 있어도 전망대로 보기 위해 순서를 이렇게 둠)
 * 시간은 속도 배율을 곱한 뒤 5분 단위로 반올림
 */
public final class StayTimes {

    public static final int OTHER = 40;
    public static final int LUNCH = 60;
    public static final int DINNER = 90;

    private static final Map<Set<String>, Integer> RULES = new LinkedHashMap<>();

    static {
        RULES.put(Set.of("observation_deck"), 60);
        RULES.put(Set.of("amusement_park", "zoo", "aquarium"), 120);
        RULES.put(Set.of("museum", "art_museum", "art_gallery"), 90);
        RULES.put(Set.of("buddhist_temple", "shinto_shrine", "hindu_temple", "place_of_worship",
                "mosque", "church"), 50);
        RULES.put(Set.of("park", "garden", "botanical_garden", "national_park", "state_park"), 75);
        RULES.put(Set.of("shopping_mall", "market", "department_store"), 90);
    }

    private StayTimes() {
    }

    /** 관광지의 기본 체류 시간 (배율 적용 전) */
    public static int baseOf(String typesCsv) {
        if (typesCsv == null || typesCsv.isBlank()) return OTHER;
        Set<String> types = Set.copyOf(Arrays.stream(typesCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        for (Map.Entry<Set<String>, Integer> rule : RULES.entrySet()) {
            if (!java.util.Collections.disjoint(types, rule.getKey())) return rule.getValue();
        }
        return OTHER;
    }

    /** 속도 배율을 곱하고 5분 단위로 반올림 (최소 5분) */
    public static int scaled(int baseMinutes, double multiplier) {
        int rounded = (int) (Math.round(baseMinutes * multiplier / 5.0) * 5);
        return Math.max(5, rounded);
    }
}