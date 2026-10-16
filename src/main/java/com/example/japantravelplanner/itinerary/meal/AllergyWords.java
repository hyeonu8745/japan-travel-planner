package com.example.japantravelplanner.itinerary.meal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 식당 장르로는 거를 수 없는 알레르기 재료 (10-16).
 * 땅콩·우유·계란처럼 거의 모든 요리에 들어가는 재료는 "이 장르를 빼면 안전"이 성립하지 않는다.
 * 빼지 못한다는 사실을 숨기지 않고 일정 요약에서 이름을 들어 "주문할 때 꼭 확인"을 안내하는 데 쓴다.
 * (메밀·새우·생선·밀가루처럼 장르와 이어지는 것은 음식 사전 food_dictionary.csv의 AVOID가 거른다)
 */
public final class AllergyWords {

    /** 화면에 보일 이름 → 알레르기 입력에서 찾을 말 */
    private static final List<String[]> WORDS = List.of(
            new String[]{"땅콩", "땅콩"}, new String[]{"견과류", "견과"}, new String[]{"호두", "호두"},
            new String[]{"아몬드", "아몬드"}, new String[]{"캐슈넛", "캐슈"}, new String[]{"피스타치오", "피스타치오"},
            new String[]{"우유", "우유"}, new String[]{"유제품", "유제품"}, new String[]{"치즈", "치즈"}, new String[]{"버터", "버터"},
            new String[]{"계란", "계란"}, new String[]{"달걀", "달걀"}, new String[]{"난류", "난류"},
            new String[]{"대두", "대두"}, new String[]{"콩", "콩"}, new String[]{"참깨", "참깨"}, new String[]{"깨", "깨"},
            new String[]{"복숭아", "복숭아"}, new String[]{"키위", "키위"}, new String[]{"바나나", "바나나"},
            new String[]{"사과", "사과"}, new String[]{"토마토", "토마토"},
            new String[]{"오징어", "오징어"}, new String[]{"문어", "문어"}, new String[]{"고등어", "고등어"},
            new String[]{"연어알", "연어알"}, new String[]{"젤라틴", "젤라틴"});

    private AllergyWords() {
    }

    /** 알레르기 입력에서 장르로 거를 수 없는 재료 이름 (입력에 나온 순서, 긴 말부터 찾아 "땅콩" 안의 "콩"이 따로 잡히지 않게) */
    public static List<String> unfilterable(String allergies) {
        if (allergies == null || allergies.isBlank()) return List.of();
        String remaining = allergies.toLowerCase(Locale.ROOT);
        List<String[]> byLength = new ArrayList<>(WORDS);
        byLength.sort(Comparator.comparingInt((String[] w) -> w[1].length()).reversed());
        List<int[]> found = new ArrayList<>();   // [위치, WORDS 순번]
        for (String[] w : byLength) {
            int at = remaining.indexOf(w[1]);
            if (at < 0) continue;
            found.add(new int[]{at, WORDS.indexOf(w)});
            remaining = remaining.replace(w[1], " ".repeat(w[1].length()));
        }
        found.sort(Comparator.comparingInt(f -> f[0]));
        return found.stream().map(f -> WORDS.get(f[1])[0]).distinct().toList();
    }
}
