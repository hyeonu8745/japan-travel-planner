package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.planner.DayWindow;
import com.example.japantravelplanner.itinerary.planner.PlannedMeal;
import com.example.japantravelplanner.itinerary.planner.ScheduledStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Trip;

import java.time.LocalTime;
import java.util.List;

/** DTO 테스트용 자료 */
final class Trips {

    private Trips() {
    }

    static Trip trip() {
        return Trip.builder().id(5L).build();
    }

    static DayWindow window() {
        return new DayWindow(LocalTime.of(16, 0), LocalTime.of(22, 0), true, true);
    }

    static ScheduledStop stop(StopType type, Attraction attraction, Restaurant restaurant) {
        if (type == StopType.ATTRACTION) {
            return new ScheduledStop(type, new ScoredAttraction(attraction, TripZone.SHINJUKU, 4.5), null,
                    LocalTime.of(16, 15), LocalTime.of(16, 55), 40, 0, new TravelLeg(10, 500, LegMode.WALK, false));
        }
        PlannedMeal planned = new PlannedMeal(type == StopType.LUNCH ? Meal.LUNCH : Meal.DINNER,
                new TravelPoint("기준", 35.70, 139.71),
                new RestaurantCandidate(restaurant, 100, 4.0, false), List.of(), false);
        return new ScheduledStop(type, null, planned, LocalTime.of(18, 0), LocalTime.of(19, 30), 90, 0,
                new TravelLeg(10, 500, LegMode.WALK, false));
    }
}