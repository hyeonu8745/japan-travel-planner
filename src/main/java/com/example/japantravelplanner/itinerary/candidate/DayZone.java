package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.itinerary.zone.TripZone;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루의 지역과 관광지 후보 (점수 높은 순)
 * @param zone    그날의 중심 지역
 * @param partner 함께 도는 이웃 지역 (10-11: 하루에 두 지역). 없으면 null
 */
public record DayZone(int dayNumber, LocalDate date, TripZone zone, List<ScoredAttraction> candidates,
                      TripZone partner) {

    public DayZone(int dayNumber, LocalDate date, TripZone zone, List<ScoredAttraction> candidates) {
        this(dayNumber, date, zone, candidates, null);
    }

    /** 그날 다닐 수 있는 지역인지 (중심 지역 또는 함께 도는 지역) */
    public boolean covers(TripZone other) {
        return other == zone || (other != null && other == partner);
    }
}
