package com.example.japantravelplanner.itinerary.planner;

import java.time.LocalTime;

/**
 * 하루 일정이 가능한 시간대와 끼니 여부.
 * - 기본 09:30 ~ 21:00 (야경 관심사면 22:00)
 * - 첫날: 숙소 도착 + 1시간부터 / 마지막 날: 출발 3시간 전까지 (모르면 기본값)
 * - 10-15: 여행 조건에 비행기 시각이 있으면 공항 이동을 넣어 계산 (AirportPlan)
 * - 점심은 13:30까지 시작해서 60분, 저녁은 18:00~20:00 사이 시작해서 90분을 넣을 수 있을 때만
 *
 * 시각은 분(0~1439)으로 계산해서 자정을 넘는 경우(23:30 도착 + 1시간)를 막는다.
 */
public record DayWindow(LocalTime start, LocalTime end, boolean lunch, boolean dinner) {

    static final int DEFAULT_START = 9 * 60 + 30;
    static final int DEFAULT_END = 21 * 60;
    static final int NIGHT_END = 22 * 60;

    static final int ARRIVAL_BUFFER = 60;         // 도착 후 짐 풀기
    static final int DEPARTURE_BUFFER = 180;      // 공항 이동·수속

    static final int LUNCH_EARLIEST = 11 * 60 + 30;
    static final int LUNCH_LATEST = 13 * 60 + 30;
    static final int LUNCH_MINUTES = 60;
    static final int DINNER_EARLIEST = 18 * 60;
    static final int DINNER_LATEST = 20 * 60;
    static final int DINNER_MINUTES = 90;

    public static DayWindow of(boolean firstDay, boolean lastDay,
                               LocalTime arrival, LocalTime departure, boolean nightView) {
        return of(firstDay, lastDay, arrival, departure, nightView, null, null);
    }

    /**
     * 10-15: 여행 조건에 비행기 시각이 있으면 그것으로 계산한 값(AirportPlan)을 쓴다.
     * 없으면 예전처럼 대화에서 들은 숙소 도착 + 1시간 / 출발 − 3시간.
     *
     * @param flightStart 첫날 관광 시작 가능 시각(분, AirportPlan.firstDayStart). 자정을 넘으면 1440 이상
     * @param flightEnd   마지막 날 관광을 끝낼 시각(분, AirportPlan.lastDayEnd). 0보다 작을 수 있다
     */
    public static DayWindow of(boolean firstDay, boolean lastDay, LocalTime arrival, LocalTime departure,
                               boolean nightView, Integer flightStart, Integer flightEnd) {
        int start = DEFAULT_START;
        int end = nightView ? NIGHT_END : DEFAULT_END;

        if (firstDay && flightStart != null) {
            start = Math.max(start, flightStart);
        } else if (firstDay && arrival != null) {
            start = Math.max(start, minutes(arrival) + ARRIVAL_BUFFER);
        }
        if (lastDay && flightEnd != null) {
            end = Math.min(end, flightEnd);
        } else if (lastDay && departure != null) {
            end = Math.min(end, minutes(departure) - DEPARTURE_BUFFER);
        }
        if (start >= end) {
            return new DayWindow(null, null, false, false);   // 일정을 넣을 수 없는 날
        }

        boolean lunch = start <= LUNCH_LATEST && end >= LUNCH_EARLIEST + LUNCH_MINUTES;
        boolean dinner = start <= DINNER_LATEST && end >= DINNER_EARLIEST + DINNER_MINUTES;
        return new DayWindow(toTime(start), toTime(end), lunch, dinner);
    }

    public boolean isEmpty() {
        return start == null;
    }

    private static int minutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime toTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }
}