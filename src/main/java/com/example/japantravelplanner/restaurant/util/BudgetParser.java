package com.example.japantravelplanner.restaurant.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 타베로그 예산 텍스트("￥3,000～￥3,999" 등)를 숫자 범위로 변환하는 유틸.
 * Spring에 의존하지 않는 순수 Java 클래스라 단위 테스트가 쉽다.
 */
public final class BudgetParser {

    // 전각 ～(U+FF5E), 파동 대시 〜(U+301C), 반각 ~ 모두 인식
    // 소스 인코딩 문제를 피하려고 유니코드 이스케이프로 작성
    private static final Pattern TILDE = Pattern.compile("[\\uFF5E\\u301C~]");
    // 쉼표 포함 숫자: 1,000 / 100,000
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*");

    /** 정적 메서드만 쓴다 */
    private BudgetParser() {
        // 유틸 클래스: 인스턴스 생성 금지
    }

    /** 예산 글자 → 범위. "-"·빈 값은 정보 없음, "￥3,000～￥3,999"는 3000~3999, "～￥999"는 0~999, "￥100,000～"은 상한 없음 */
    public static BudgetRange parse(String raw) {
        if (raw == null) {
            return BudgetRange.UNKNOWN;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.equals("-")) {
            return BudgetRange.UNKNOWN;
        }

        Matcher tilde = TILDE.matcher(s);
        if (!tilde.find()) {
            // 물결표 없이 단일 금액만 있는 경우 (현재 데이터엔 없지만 방어)
            Integer value = firstNumber(s);
            return value == null ? BudgetRange.UNKNOWN : new BudgetRange(value, value);
        }

        Integer min = firstNumber(s.substring(0, tilde.start()));
        Integer max = firstNumber(s.substring(tilde.end()));

        if (min == null && max == null) {
            return BudgetRange.UNKNOWN;
        }
        // "～￥999"처럼 하한이 없으면 0엔부터
        return new BudgetRange(min == null ? 0 : min, max);
    }

    /** 글자에서 첫 번째 숫자 (쉼표 제거) */
    private static Integer firstNumber(String text) {
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group().replace(",", ""));
    }
}