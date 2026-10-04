package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;

import java.time.LocalTime;

/**
 * 시각이 정해진 일정 항목 하나
 * @param attraction  관광지 (type이 ATTRACTION일 때)
 * @param meal        식당 (type이 LUNCH·DINNER일 때). 다음 후보·장르 중복 여부도 여기에 있다
 * @param arrival     도착해서 시작하는 시각 (식사는 대기가 끝난 뒤 실제 식사 시작 시각)
 * @param waitMinutes 식사 시간대를 기다린 시간 (화면에 "여유 시간"). 먼저 도착한 만큼
 * @param travelIn    앞 장소에서 오는 이동 (첫 항목은 숙소에서). 시간표에는 이동 + 10분 여유가 들어 있다
 */
public record ScheduledStop(StopType type,
                            ScoredAttraction attraction,
                            PlannedMeal meal,
                            LocalTime arrival,
                            LocalTime departure,
                            int stayMinutes,
                            int waitMinutes,
                            TravelLeg travelIn) {

    public String name() {
        return type == StopType.ATTRACTION
                ? attraction.attraction().getName()
                : meal.chosen().restaurant().getName();
    }
}