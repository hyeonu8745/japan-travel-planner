package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.zone.TripZone;

import java.time.LocalDate;
import java.util.List;

/**
 * 시각까지 정해진 하루
 * @param stops    시간 순서대로 (관광지, 점심, 관광지, 저녁, 야경)
 * @param dropped  시간이 안 맞아 빠진 관광지
 * @param warnings 화면에 안내할 문장 (예: 식당 후보 없음)
 */
public record ScheduledDay(int dayNumber, LocalDate date, TripZone zone,
                           List<ScheduledStop> stops,
                           List<DroppedStop> dropped,
                           List<String> warnings) {
}