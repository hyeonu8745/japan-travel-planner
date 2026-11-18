package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.planner.DayWindow;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * 하루를 통째로 쓰는 테마파크 (USJ·후지큐 하이랜드·레고랜드 등).
 * 놀이공원 체류 120분으로 넣으면 USJ 를 두 시간 보고 가이유칸·쓰텐카쿠까지 도는 일정이 나왔다 (11-18 오사카).
 * 그래서 이런 곳이 그날 방문 후보에 들어가면 그날은 그곳 하나만 (점심은 파크 안에서, 저녁·야경은 그대로),
 * 하루를 다 쓸 수 없는 날(늦게 도착한 첫날·일찍 떠나는 마지막 날)과 짧은 여행(2일 이하)에는 후보에서 뺀다
 * (2일 가와구치코 여행의 하루가 후지큐 하이랜드로 채워졌다. 꼭 가고 싶으면 대화·채팅 수정으로 넣는다).
 */
public final class FullDayParks {

    /** 하루 일정으로 볼 곳 (관광지 이름 그대로) */
    static final Set<String> NAMES = Set.of(
            "ユニバーサル・スタジオ・ジャパン", "富士急ハイランド", "レゴランド・ジャパン",
            "東京ディズニーランド", "東京ディズニーシー");

    /** 파크 안에서 보내는 시간 (여행 속도 배율을 곱하지 않는다) */
    public static final int STAY_MINUTES = 420;

    /** 이 시각보다 늦게 시작하거나 이 시각보다 일찍 끝나는 날은 하루짜리 파크를 넣지 않는다 */
    static final LocalTime LATEST_START = LocalTime.of(10, 30);
    static final LocalTime EARLIEST_END = LocalTime.of(19, 0);

    /** 이보다 짧은 여행에는 하루짜리 파크를 넣지 않는다 */
    static final int MIN_TRIP_DAYS = 3;

    /** 점심을 따로 넣지 않을 때 화면 안내 */
    public static final String NOTE = "하루를 쓰는 테마파크라 이날은 이곳만 넣었어요. 점심은 파크 안에서 드세요.";

    /** 정적 메서드만 쓴다 */
    private FullDayParks() {
    }

    /** 하루를 통째로 쓰는 곳인지 */
    public static boolean isFullDay(Attraction attraction) {
        return attraction != null && attraction.getName() != null && NAMES.contains(attraction.getName());
    }

    /** 하루를 다 쓸 수 있는 날인지 (아침에 시작해서 저녁까지) */
    public static boolean fitsWholeDay(DayWindow window) {
        return window != null && window.start() != null && window.end() != null
                && !window.start().isAfter(LATEST_START) && !window.end().isBefore(EARLIEST_END);
    }

    /**
     * 그날 후보 정리. 그날 방문 수(perDay) 안에 하루짜리 파크가 있고 하루를 다 쓸 수 있는 날(3일 이상 여행)이면 그 파크 하나만,
     * 아니면 하루짜리 파크를 후보에서 뺀다 (예비 후보로 남아 짧게 들어가지 않게)
     */
    public static DayZone shape(DayZone day, int perDay, DayWindow window, int tripDays) {
        List<ScoredAttraction> candidates = day.candidates();
        if (candidates.stream().noneMatch(c -> isFullDay(c.attraction()))) return day;
        ScoredAttraction park = candidates.stream().limit(perDay)
                .filter(c -> isFullDay(c.attraction())).findFirst().orElse(null);
        List<ScoredAttraction> shaped = park != null && fitsWholeDay(window) && tripDays >= MIN_TRIP_DAYS
                ? List.of(park)
                : candidates.stream().filter(c -> !isFullDay(c.attraction())).toList();
        return new DayZone(day.dayNumber(), day.date(), day.zone(), shaped, day.partner());
    }

    /** 그날이 하루짜리 파크 날인지 (후보가 그 파크 하나) */
    public static boolean isParkDay(DayZone day) {
        return day.candidates().size() == 1 && isFullDay(day.candidates().get(0).attraction());
    }
}
