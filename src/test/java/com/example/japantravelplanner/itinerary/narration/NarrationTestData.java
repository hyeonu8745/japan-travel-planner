package com.example.japantravelplanner.itinerary.narration;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.planner.*;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 설명 테스트용 일정 자료 */
final class NarrationTestData {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final TravelLeg LEG = new TravelLeg(5, 300, LegMode.WALK, false);

    private NarrationTestData() {
    }

    static ScheduledStop attraction(String name, String types, String time) {
        Attraction a = Attraction.builder().id(1L).name(name).types(types)
                .location(GF.createPoint(new Coordinate(139.70, 35.69))).build();
        LocalTime t = LocalTime.parse(time);
        return new ScheduledStop(StopType.ATTRACTION, new ScoredAttraction(a, TripZone.SHINJUKU, 4.5), null,
                t, t.plusMinutes(40), 40, 0, LEG);
    }

    static ScheduledStop meal(StopType type, String name, String genre, String time) {
        Restaurant r = Restaurant.builder().id(9L).name(name).genre(genre)
                .location(GF.createPoint(new Coordinate(139.70, 35.69))).build();
        Meal meal = type == StopType.LUNCH ? Meal.LUNCH : Meal.DINNER;
        PlannedMeal planned = new PlannedMeal(meal, new TravelPoint("기준", 35.69, 139.70),
                new RestaurantCandidate(r, 100, 4.0, false), List.of(), false);
        LocalTime t = LocalTime.parse(time);
        return new ScheduledStop(type, null, planned, t, t.plusMinutes(60), 60, 0, LEG);
    }

    static ScheduledDay day(int number, List<ScheduledStop> stops, List<DroppedStop> dropped) {
        return new ScheduledDay(number, LocalDate.of(2026, 10, 19 + number), TripZone.SHINJUKU,
                stops, dropped, List.of());
    }
}