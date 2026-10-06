package com.example.japantravelplanner.itinerary.meal;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 타베로그의 정기 휴일 문구("日曜日", "土曜日、日曜日、祝日", "月曜日、金曜日" 등)에서 쉬는 요일을 읽는다.
 *
 * 확실하게 요일만 읽히는 경우에만 쉬는 날로 본다. 문구가 애매하면 쉬는 날이 없는 것으로 본다.
 * (괜찮은 식당을 잘못 빼는 것보다, 휴일에 걸리는 식당 하나를 놓치는 쪽이 낫다.
 *  어차피 휴일 정보는 정기 휴일뿐이라 임시 휴무는 알 수 없다)
 *
 * 쉬는 날이 없다고 보는 경우: 無休, なし, 不定休, 第2·第4처럼 몇째 주를 말하는 경우, 範囲(月～金), 以外(월요일 이외) 등
 * 공휴일(祝日)은 일본 공휴일 표가 필요해서 아직 판단하지 않는다.
 */
public final class RegularHoliday {

    private static final String WEEKDAY_CHARS = "月火水木金土日";

    // 이 낱말이 있으면 요일만으로 쉬는 날을 말할 수 없다
    private static final List<String> OPEN_MARKERS = List.of("無休", "なし", "ナシ");
    private static final List<String> UNCERTAIN_MARKERS = List.of(
            "不定", "第", "隔週", "毎月", "月に", "月1", "月2", "月3", "不規則", "臨時", "以外", "除く", "~", "〜");

    // 요일을 읽기 전에 지워도 되는 낱말 (긴 것부터 지워야 "祝日"가 "祝"보다 먼저 지워진다)
    private static final List<String> NOISE = List.of(
            "年末年始", "祝前日", "祝日", "祝", "お盆", "夏季", "冬季", "連休", "毎週", "定休日", "定休", "休業日", "休業",
            "休み", "曜日", "曜", "休");

    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)");
    private static final Pattern SEPARATORS = Pattern.compile("[、,，・/／\\s&＆]");

    private RegularHoliday() {
    }

    /** 이 문구에서 확실하게 읽히는 쉬는 요일. 판단할 수 없으면 빈 집합 */
    public static Set<DayOfWeek> closedDays(String holidayText) {
        if (holidayText == null || holidayText.isBlank()) {
            return Set.of();
        }
        // 전각 문자(숫자, 괄호, 물결)를 반각으로 맞춘다
        String text = Normalizer.normalize(holidayText, Normalizer.Form.NFKC);

        if (containsAny(text, OPEN_MARKERS) || containsAny(text, UNCERTAIN_MARKERS)) {
            return Set.of();
        }

        text = PARENTHESES.matcher(text).replaceAll("");   // 「月曜日(祝日の場合は翌日)」의 부가 설명은 읽지 않는다
        for (String noise : NOISE) {
            text = text.replace(noise, "");
        }
        text = SEPARATORS.matcher(text).replaceAll("");

        // 지우고 남은 것이 요일 글자뿐이어야 한다. 모르는 낱말이 섞여 있으면 판단하지 않는다
        if (text.isEmpty()) {
            return Set.of();
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (char c : text.toCharArray()) {
            if (WEEKDAY_CHARS.indexOf(c) < 0) {
                return Set.of();
            }
            days.add(toDayOfWeek(c));
        }
        return days;
    }

    /** 그 날짜가 이 가게의 정기 휴일 요일인지 (판단할 수 없으면 false) */
    public static boolean isClosedOn(String holidayText, LocalDate date) {
        return date != null && closedDays(holidayText).contains(date.getDayOfWeek());
    }

    private static boolean containsAny(String text, List<String> markers) {
        return markers.stream().anyMatch(text::contains);
    }

    private static DayOfWeek toDayOfWeek(char c) {
        return switch (c) {
            case '月' -> DayOfWeek.MONDAY;
            case '火' -> DayOfWeek.TUESDAY;
            case '水' -> DayOfWeek.WEDNESDAY;
            case '木' -> DayOfWeek.THURSDAY;
            case '金' -> DayOfWeek.FRIDAY;
            case '土' -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }
}