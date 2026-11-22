package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 날짜로 문을 닫는 관광지 (11-22). 그날 닫힌 관광지는 그날 후보에서 뺀다.
 *
 * - OSM 영업시간(opening_hours)의 날짜 휴무: "Dec 28-Jan 04 off", "Dec 29-31 off", "Jan 01 off", "Aug 10-20, Dec 24-Jan 07 off"
 *   (요일 휴무 "Mo off" 는 여기서 보지 않는다: 확장 8단계 휴관일 안내에서 따로)
 * - 개방 기간이 해마다 요일로 정해지는 곳은 직접 적는다 ({@link #OPEN_SEASONS}):
 *   若草山 = 3월 셋째 토요일 ~ 12월 둘째 일요일 (겨울에는 산에 오를 수 없다)
 */
public final class SeasonalClosures {

    /** 정적 메서드만 쓴다 */
    private SeasonalClosures() {
    }

    /** 해마다 개방 기간이 있는 곳: 이름 → 그해 [개방일, 마지막 날] */
    @FunctionalInterface
    interface Season {
        LocalDate[] of(int year);
    }

    static final Map<String, Season> OPEN_SEASONS = Map.of(
            "若草山", year -> new LocalDate[]{
                    nth(year, 3, DayOfWeek.SATURDAY, 3),
                    nth(year, 12, DayOfWeek.SUNDAY, 2)});

    /** "Dec 28-Jan 04", "Dec 29-31", "Jan 01" 가 쉼표로 이어지고 끝이 off */
    private static final Pattern OFF_SEGMENT = Pattern.compile(
            "^((?:[A-Z][a-z]{2}\\s?\\d{1,2}(?:\\s?-\\s?(?:[A-Z][a-z]{2}\\s?)?\\d{1,2})?\\s*,?\\s*)+)\\s*(?:off|closed)\\b.*$");
    private static final Pattern RANGE = Pattern.compile(
            "([A-Z][a-z]{2})\\s?(\\d{1,2})(?:\\s?-\\s?(?:([A-Z][a-z]{2})\\s?)?(\\d{1,2}))?");

    /** 그날 문을 닫는지 */
    public static boolean closedOn(Attraction attraction, LocalDate date) {
        if (attraction == null || date == null) return false;
        Season season = OPEN_SEASONS.get(attraction.getName());
        if (season != null) {
            LocalDate[] open = season.of(date.getYear());
            if (date.isBefore(open[0]) || date.isAfter(open[1])) return true;
        }
        return closedByOpeningHours(attraction.getOpeningHours(), date);
    }

    /** OSM 영업시간의 날짜 휴무에 그날이 들어가는지 */
    static boolean closedByOpeningHours(String openingHours, LocalDate date) {
        if (openingHours == null || openingHours.isBlank()) return false;
        MonthDay day = MonthDay.from(date);
        for (String raw : openingHours.split(";")) {
            Matcher segment = OFF_SEGMENT.matcher(raw.strip());
            if (!segment.matches()) continue;
            Matcher range = RANGE.matcher(segment.group(1));
            while (range.find()) {
                Integer fromMonth = month(range.group(1));
                if (fromMonth == null) continue;
                Integer toMonth = range.group(3) == null ? fromMonth : month(range.group(3));
                if (toMonth == null) continue;
                int fromDay = Integer.parseInt(range.group(2));
                int toDay = range.group(4) == null ? fromDay : Integer.parseInt(range.group(4));
                MonthDay from = safe(fromMonth, fromDay);
                MonthDay to = safe(toMonth, toDay);
                if (length(from, to) > MAX_OFF_DAYS) continue;   // "Dec 29-Jun 03" 처럼 잘못 적힌 긴 휴무는 믿지 않는다
                if (within(day, from, to)) return true;
            }
        }
        return false;
    }

    /** 날짜 휴무로 믿는 최대 길이 (연말연시·여름 휴무 정도). 더 길면 데이터 오류로 본다 */
    static final int MAX_OFF_DAYS = 60;

    /** from ~ to 날 수 (해를 넘기면 다음 해까지) */
    private static int length(MonthDay from, MonthDay to) {
        int a = from.atYear(2025).getDayOfYear();
        int b = to.atYear(2025).getDayOfYear();
        return b >= a ? b - a + 1 : 365 - a + b + 1;
    }

    /** from ~ to 안인지 (해를 넘기는 범위 Dec 28 ~ Jan 04 포함) */
    private static boolean within(MonthDay day, MonthDay from, MonthDay to) {
        if (!from.isAfter(to)) return !day.isBefore(from) && !day.isAfter(to);
        return !day.isBefore(from) || !day.isAfter(to);
    }

    /** 2월 30일 같은 잘못된 날은 그달 마지막 날로 */
    private static MonthDay safe(int month, int day) {
        int max = java.time.Month.of(month).maxLength();
        return MonthDay.of(month, Math.min(Math.max(day, 1), max));
    }

    /** "Jan" → 1. 모르면 null */
    private static Integer month(String name) {
        if (name == null) return null;
        String m = name.toLowerCase(Locale.ROOT);
        String[] names = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < names.length; i++) if (names[i].equals(m)) return i + 1;
        return null;
    }

    /** 그달 n번째 요일 */
    static LocalDate nth(int year, int month, DayOfWeek dayOfWeek, int n) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dayOfWeek));
    }
}
