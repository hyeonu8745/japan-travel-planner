package com.example.japantravelplanner.itinerary.reason;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Interest;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** 고른 이유 테스트용 자료 */
final class ReasonFixtures {

    private ReasonFixtures() {
    }

    static Attraction attraction(String name, String types, double rating, int reviews) {
        return Attraction.builder().id((long) name.hashCode()).name(name).types(types)
                .rating(rating).reviewCnt(reviews).build();
    }

    static Restaurant restaurant(String name, String genre, double rating) {
        return Restaurant.builder().id((long) name.hashCode()).name(name).genre(genre).ratingVal(rating).build();
    }

    static ItineraryStop attractionStop(int order, Attraction a, LegMode mode, int minutes) {
        return base(order, StopType.ATTRACTION, mode, minutes).attraction(a).build();
    }

    static ItineraryStop mealStop(int order, StopType type, Restaurant r, boolean budgetRelaxed, boolean genreRepeated) {
        return base(order, type, LegMode.TRANSIT, 20).restaurant(r)
                .budgetRelaxed(budgetRelaxed).genreRepeated(genreRepeated).build();
    }

    private static ItineraryStop.ItineraryStopBuilder base(int order, StopType type, LegMode mode, int minutes) {
        return ItineraryStop.builder().stopOrder(order).type(type)
                .arrivalTime(LocalTime.of(10, 0)).departureTime(LocalTime.of(11, 0)).stayMinutes(60)
                .travelMode(mode).travelMinutes(minutes).travelDistanceMeters(minutes * 80);
    }

    static ReasonContext context(List<Interest> interests, Set<String> prefer, Set<String> avoid, boolean avoidSmoky) {
        return new ReasonContext(interests, new FoodFilter(prefer, avoid, avoidSmoky));
    }
}
