package com.example.japantravelplanner.itinerary.service;

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
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.entity.Trip;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 일정 저장 테스트용 자료 */
final class ItineraryTestData {

    static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    static final TravelLeg WALK_5 = new TravelLeg(5, 300, LegMode.WALK, false);

    private ItineraryTestData() {
    }

    static Trip trip() {
        return Trip.builder().id(5L)
                .startDate(LocalDate.of(2026, 10, 20)).endDate(LocalDate.of(2026, 10, 21))
                .travelers(2).budget(1_600_000).travelPace(TravelPace.NORMAL)
                .interests(List.of(Interest.FOOD, Interest.NIGHT_VIEW))
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .accommodationName("숙소").accommodationAddress("도쿄 신주쿠구 가부키초")
                .build();
    }

    static Attraction attraction(long id, String name, String types) {
        return Attraction.builder().id(id).name(name).types(types).rating(4.5).reviewCnt(1000)
                .address("도쿄").location(GF.createPoint(new Coordinate(139.70, 35.69))).build();
    }

    static Restaurant restaurant(long id, String name, String genre) {
        return Restaurant.builder().id(id).name(name).genre(genre).ratingVal(3.8)
                .budgetLunch("～￥999").budgetDinner("￥3,000～￥3,999").address("도쿄 식당 주소")
                .location(GF.createPoint(new Coordinate(139.71, 35.70))).build();
    }

    static ScheduledStop attractionStop(Attraction a, String arrival, String departure, int stay) {
        return new ScheduledStop(StopType.ATTRACTION, new ScoredAttraction(a, TripZone.SHINJUKU, 4.5), null,
                LocalTime.parse(arrival), LocalTime.parse(departure), stay, 0, WALK_5);
    }

    static ScheduledStop mealStop(StopType type, Restaurant r, boolean budgetRelaxed, boolean genreRepeated,
                                  String arrival, String departure, int wait) {
        PlannedMeal planned = new PlannedMeal(type == StopType.LUNCH ? Meal.LUNCH : Meal.DINNER,
                new TravelPoint("기준", 35.70, 139.71),
                new RestaurantCandidate(r, 100, 4.0, budgetRelaxed), List.of(), genreRepeated);
        return new ScheduledStop(type, null, planned, LocalTime.parse(arrival), LocalTime.parse(departure),
                type == StopType.LUNCH ? 60 : 90, wait, new TravelLeg(10, 763, LegMode.WALK, false));
    }

    static ScheduledDay day(int number, List<ScheduledStop> stops, List<DroppedStop> dropped, List<String> warnings) {
        return new ScheduledDay(number, LocalDate.of(2026, 10, 19 + number), TripZone.SHINJUKU, stops, dropped, warnings);
    }

    static DayWindow window(String start, String end) {
        return new DayWindow(LocalTime.parse(start), LocalTime.parse(end), true, true);
    }

    static final DayWindow NO_WINDOW = new DayWindow(null, null, false, false);

    /** 고른 식당 다음 후보(alternatives)가 있는 식사 항목 */
    static ScheduledStop mealStopWithAlternatives(StopType type, Restaurant chosen, List<Restaurant> alternatives,
                                                  String arrival, String departure) {
        PlannedMeal planned = new PlannedMeal(type == StopType.LUNCH ? Meal.LUNCH : Meal.DINNER,
                new TravelPoint("기준", 35.70, 139.71),
                new RestaurantCandidate(chosen, 100, 4.0, false),
                alternatives.stream().map(r -> new RestaurantCandidate(r, 200, 3.9, false)).toList(), false);
        return new ScheduledStop(type, null, planned, LocalTime.parse(arrival), LocalTime.parse(departure),
                type == StopType.LUNCH ? 60 : 90, 0, new TravelLeg(10, 763, LegMode.WALK, false));
    }
}