package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.candidate.*;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.meal.*;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.*;

/** 일정 생성 ②③ 단계 확인용. 일정 생성 Service가 생기면 그쪽으로 흡수한다 */
@Service
@RequiredArgsConstructor
public class ItineraryPreviewService {

    private final TripRepository tripRepository;
    private final TripPreferenceRepository preferenceRepository;
    private final AttractionRepository attractionRepository;
    private final AccommodationLocator accommodationLocator;
    private final AttractionScorer scorer;
    private final DayZonePlanner dayZonePlanner;
    private final PlaceTypeLabels typeLabels;
    private final MealBudgetCalculator mealBudgetCalculator;
    private final FoodFilterResolver foodFilterResolver;
    private final RestaurantFinder restaurantFinder;

    @Transactional
    public List<Map<String, Object>> previewCandidates(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);

        Point hotel = accommodationLocator.locate(trip);
        int days = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        int perDay = trip.getTravelPace().attractionsPerDay();
        var interests = trip.getInterests() == null ? List.<com.example.japantravelplanner.trip.entity.Interest>of() : trip.getInterests();

        List<ScoredAttraction> scored = scorer.score(attractionRepository.findAll(), interests);
        List<DayZone> plan = dayZonePlanner.plan(scored, trip.getStartDate(), days, perDay, interests,
                preference == null ? List.of() : preference.getConstraints(),
                hotel.getY(), hotel.getX());

        List<Map<String, Object>> result = new ArrayList<>();
        for (DayZone day : plan) {
            List<Map<String, Object>> candidates = day.candidates().stream()
                    .map(c -> Map.<String, Object>of(
                            "name", c.attraction().getName(),
                            "score", Math.round(c.score() * 100) / 100.0,
                            "rating", c.attraction().getRating(),
                            "reviews", c.attraction().getReviewCnt(),
                            "labels", typeLabels.toVisibleLabels(c.attraction().getTypes())))
                    .toList();
            result.add(Map.of(
                    "day", day.dayNumber(),
                    "date", day.date().toString(),
                    "zone", day.zone().getLabel(),
                    "candidates", candidates));
        }
        return result;
    }
    @Transactional(readOnly = true)
    public Map<String, Object> previewRestaurants(Long userId, Long tripId, double lat, double lng, Meal meal) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);

        int days = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        double foodRatio = preference == null ? 0.4 : preference.getFoodBudgetRatio();
        MealBudget budget = mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), days, foodRatio);
        FoodFilter filter = foodFilterResolver.resolve(
                preference == null ? List.of() : preference.getConstraints(), trip.getAllergies());

        List<Map<String, Object>> candidates = restaurantFinder
                .find(lat, lng, meal, budget.of(meal), filter, Set.of(), 5).stream()
                .map(c -> Map.<String, Object>of(
                        "name", c.restaurant().getName(),
                        "genre", c.restaurant().getGenre(),
                        "rating", c.restaurant().getRatingVal(),
                        "budget", meal == Meal.LUNCH ? c.restaurant().getBudgetLunch() : c.restaurant().getBudgetDinner(),
                        "distanceMeters", c.distanceMeters(),
                        "score", Math.round(c.score() * 100) / 100.0,
                        "budgetRelaxed", c.budgetRelaxed()))
                .toList();

        return Map.of(
                "mealBudget", Map.of("lunchMaxYen", budget.lunchMaxYen(), "dinnerMaxYen", budget.dinnerMaxYen()),
                "preferGenres", filter.preferGenres(),
                "avoidGenres", filter.avoidGenres(),
                "avoidSmoky", filter.avoidSmoky(),
                "candidates", candidates);
    }
}