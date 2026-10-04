package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;

import java.util.List;

/**
 * 하루의 관광지 방문 순서 (끼니는 아직 없음)
 * @param morning       오전 관광지 (방문 순서)
 * @param afternoon     오후 관광지 (방문 순서)
 * @param night         저녁 식사 후 야경 장소. 야경 관심사가 없거나 전망대 후보가 없으면 null
 * @param spares        시간이 남을 때 넣거나, 뺀 자리를 채울 예비 후보 (점수순)
 * @param legs          숙소 → 첫 관광지, 그다음 관광지마다 들어오는 구간 (morning + afternoon 순서와 같은 길이)
 * @param endLeg        마지막 낮 관광지 → 야경 장소(있으면) 또는 숙소. 순서 비교용
 * @param travelMinutes legs + endLeg 이동 시간 합
 */
public record DayRoute(DayZone day,
                       List<ScoredAttraction> morning,
                       List<ScoredAttraction> afternoon,
                       ScoredAttraction night,
                       List<ScoredAttraction> spares,
                       List<TravelLeg> legs,
                       TravelLeg endLeg,
                       int travelMinutes) {

    public static DayRoute empty(DayZone day) {
        return new DayRoute(day, List.of(), List.of(), null, List.of(), List.of(), null, 0);
    }

    /** 오전 + 오후 방문 순서 */
    public List<ScoredAttraction> daytime() {
        return java.util.stream.Stream.concat(morning.stream(), afternoon.stream()).toList();
    }
}