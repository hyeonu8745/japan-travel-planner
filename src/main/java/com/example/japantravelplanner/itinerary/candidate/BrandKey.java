package com.example.japantravelplanner.itinerary.candidate;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * 같은 가게의 다른 지점을 하나로 보기 위한 이름 (관광지).
 * 10-10 여행 8 v10: "Chopsticks Studio Ginza / Shinjuku / Shibuya"가 2·5·6일차에 하나씩 들어갔다.
 * 마지막 단어가 지점 표시(○○店, 지역 이름)면 그 단어를 뺀 이름을 쓴다. 아니면 이름 그대로.
 */
public final class BrandKey {

    /** 지점 이름으로 쓰이는 지역 (영문은 소문자로 비교) */
    static final Set<String> AREA_WORDS = Set.of(
            "ginza", "shinjuku", "shibuya", "asakusa", "harajuku", "akihabara", "ueno", "roppongi", "odaiba",
            "ikebukuro", "omotesando", "tokyo", "銀座", "新宿", "渋谷", "浅草", "原宿", "秋葉原", "上野", "六本木",
            "お台場", "池袋", "表参道", "東京");

    private BrandKey() {
    }

    public static String of(String name) {
        if (name == null) return "";
        String[] tokens = name.trim().split("\\s+");
        if (tokens.length < 2) return name.trim();
        String last = tokens[tokens.length - 1];
        boolean branch = last.endsWith("店") || AREA_WORDS.contains(last.toLowerCase(Locale.ROOT));
        return branch ? String.join(" ", Arrays.copyOf(tokens, tokens.length - 1)) : name.trim();
    }
}
