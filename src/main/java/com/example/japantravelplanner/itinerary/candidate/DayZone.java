package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.itinerary.zone.TripZone;

import java.time.LocalDate;
import java.util.List;

/** 하루의 지역과 관광지 후보 (점수 높은 순) */
public record DayZone(int dayNumber, LocalDate date, TripZone zone, List<ScoredAttraction> candidates) {
}