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

    /**
     * 띄어쓰기 없이 지역 이름이 붙은 지점 ("忍者体験カフェ原宿" ↔ "忍者体験カフェ浅草", 10-11 여행 8 v12).
     * 일본어 지역 이름으로 끝나고 앞부분이 3글자 이상일 때만 뺀다 ("新宿御苑", "浅草寺"처럼 지역 이름으로 끝나지 않으면 그대로)
     */
    static String withoutGluedArea(String name) {
        for (String area : AREA_WORDS) {
            if (area.chars().allMatch(ch -> ch < 128)) continue;   // 영문은 띄어 쓰는 경우만 본다
            if (name.endsWith(area) && name.length() - area.length() >= 3) {
                return name.substring(0, name.length() - area.length());
            }
        }
        return name;
    }

    public static String of(String name) {
        if (name == null) return "";
        String[] tokens = name.trim().split("\\s+");
        if (tokens.length < 2) return withoutGluedArea(name.trim());
        String last = tokens[tokens.length - 1];
        boolean branch = last.endsWith("店") || AREA_WORDS.contains(last.toLowerCase(Locale.ROOT));
        return branch ? String.join(" ", Arrays.copyOf(tokens, tokens.length - 1)) : name.trim();
    }
}
