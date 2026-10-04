package com.example.japantravelplanner.itinerary.narration;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * AI가 쓴 하루 설명이 규칙을 지켰는지 서버가 확인한다.
 * 프롬프트로 부탁해도 가끔 어기기 때문에, 확인할 수 있는 것은 코드로 막는다. (가격·영업 정보 단정 금지, 시각 금지, 평문)
 * 어긴 설명은 고쳐 쓰지 않고 버린다. 설명이 없어도 일정은 정상이다.
 */
@Slf4j
public final class DescriptionGuard {

    static final int MAX_CHARS = 350;

    private record Rule(String name, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("가격·요금", Pattern.compile("\\d[\\d,]*\\s*(엔|円|원)|입장료|입장권|요금|무료\\s?입장")),
            new Rule("영업·운영 정보", Pattern.compile("영업\\s?시간|운영\\s?시간|휴무|정기\\s?휴일|예약\\s?필수|웨이팅")),
            // "2시간", "분위기"는 허용: 시 뒤에 간, 분 뒤에 위가 오는 경우는 제외
            new Rule("숫자 시각·이동 시간", Pattern.compile("\\d{1,2}:\\d{2}|\\d{1,2}\\s*시(?!간)|\\d+\\s*분(?!위)")),
            new Rule("링크·태그", Pattern.compile("https?://|www\\.|<[^>]+>"))
    );

    private DescriptionGuard() {
    }

    /** 공백을 정리해서 돌려준다. 비었거나 규칙을 어겼으면 null */
    public static String clean(String raw) {
        if (raw == null) return null;
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return null;

        if (text.length() > MAX_CHARS) {
            log.warn("[Narration] 설명 제외: 너무 김 ({}자)", text.length());
            return null;
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                log.warn("[Narration] 설명 제외: {} 포함", rule.name());
                return null;
            }
        }
        return text;
    }
}