package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.candidate.*;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.meal.*;
import com.example.japantravelplanner.itinerary.planner.*;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
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

/** 일정 생성 단계별 확인용. 일정 생성 Service가 생기면 그쪽으로 흡수한다 */
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
    private final RouteOptimizer routeOptimizer;
    private final TravelProperties travelProperties;
    private final MealPlanner mealPlanner;

    /** ②③ 날짜별 지역과 관광지 후보 */
    @Transactional
    public List<Map<String, Object>> previewCandidates(Long userId, Long tripId) {
        Trip trip = findTrip(userId, tripId);
        List<PreferenceConstraint> constraints = constraintsOf(tripId);
        Point hotel = accommodationLocator.locate(trip);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DayZone day : planZones(trip, constraints, hotel)) {
            List<Map<String, Object>> candidates = day.candidates().stream()
                    .map(this::attractionView)
                    .toList();
            result.add(Map.of(
                    "day", day.dayNumber(),
                    "date", day.date().toString(),
                    "zone", day.zone().getLabel(),
                    "candidates", candidates));
        }
        return result;
    }

    /** 끼니 후보 (한 지점 기준) */
    @Transactional(readOnly = true)
    public Map<String, Object> previewRestaurants(Long userId, Long tripId, double lat, double lng, Meal meal) {
        Trip trip = findTrip(userId, tripId);
        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);

        double foodRatio = preference == null ? 0.4 : preference.getFoodBudgetRatio();
        MealBudget budget = mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), daysOf(trip), foodRatio);
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
                        "score", round2(c.score()),
                        "budgetRelaxed", c.budgetRelaxed()))
                .toList();

        return Map.of(
                "mealBudget", Map.of("lunchMaxYen", budget.lunchMaxYen(), "dinnerMaxYen", budget.dinnerMaxYen()),
                "preferGenres", filter.preferGenres(),
                "avoidGenres", filter.avoidGenres(),
                "avoidSmoky", filter.avoidSmoky(),
                "candidates", candidates);
    }

    /** ④⑤ 날짜별 관광지 방문 순서(TSP) + 점심·저녁 */
    @Transactional
    public Map<String, Object> previewRoutes(Long userId, Long tripId) {
        Trip trip = findTrip(userId, tripId);
        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);
        List<PreferenceConstraint> constraints = preference == null ? List.of() : preference.getConstraints();
        Point hotel = accommodationLocator.locate(trip);

        TravelOptions options = TravelOptions.of(trip, constraints, travelProperties);
        TravelPoint hotelPoint = new TravelPoint(trip.getAccommodationName(), hotel.getY(), hotel.getX());
        boolean nightView = interestsOf(trip).contains(Interest.NIGHT_VIEW);

        int days = daysOf(trip);
        double foodRatio = preference == null ? 0.4 : preference.getFoodBudgetRatio();
        MealBudget budget = mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), days, foodRatio);
        FoodFilter filter = foodFilterResolver.resolve(constraints, trip.getAllergies());
        MealHistory history = new MealHistory();

        List<Map<String, Object>> dayViews = new ArrayList<>();
        for (DayZone day : planZones(trip, constraints, hotel)) {
            DayWindow window = DayWindow.of(day.dayNumber() == 1, day.dayNumber() == days,
                    preference == null ? null : preference.getArrivalTime(),
                    preference == null ? null : preference.getDepartureTime(),
                    nightView);
            DayRoute route = routeOptimizer.optimize(day, hotelPoint, trip.getTravelPace(), options, nightView);
            DayMeals meals = mealPlanner.plan(route, hotelPoint, window, budget, filter, history);
            dayViews.add(routeView(route, window, meals));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", options.mode());
        result.put("walkThresholdMinutes", options.walkThresholdMinutes());
        result.put("nightView", nightView);
        result.put("mealBudget", Map.of("lunchMaxYen", budget.lunchMaxYen(), "dinnerMaxYen", budget.dinnerMaxYen()));
        result.put("days", dayViews);
        return result;
    }

    // ── 공통 ──

    private Trip findTrip(Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
    }

    private List<PreferenceConstraint> constraintsOf(Long tripId) {
        return preferenceRepository.findByTripId(tripId)
                .map(TripPreference::getConstraints)
                .orElse(List.of());
    }

    private List<DayZone> planZones(Trip trip, List<PreferenceConstraint> constraints, Point hotel) {
        List<Interest> interests = interestsOf(trip);
        List<ScoredAttraction> scored = scorer.score(attractionRepository.findAll(), interests);
        return dayZonePlanner.plan(scored, trip.getStartDate(), daysOf(trip),
                trip.getTravelPace().attractionsPerDay(), interests, constraints,
                hotel.getY(), hotel.getX());
    }

    private static List<Interest> interestsOf(Trip trip) {
        return trip.getInterests() == null ? List.of() : trip.getInterests();
    }

    private static int daysOf(Trip trip) {
        return (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }

    // ── 응답 모양 (확인용) ──

    private Map<String, Object> routeView(DayRoute route, DayWindow window, DayMeals meals) {
        List<ScoredAttraction> daytime = route.daytime();
        List<Map<String, Object>> stops = new ArrayList<>();
        for (int i = 0; i < daytime.size(); i++) {
            Map<String, Object> stop = new LinkedHashMap<>();
            stop.put("order", i + 1);
            stop.put("slot", i < route.morning().size() ? "오전" : "오후");
            stop.put("name", daytime.get(i).attraction().getName());
            stop.put("score", round2(daytime.get(i).score()));
            stop.put("travelIn", legView(route.legs().get(i)));
            stops.add(stop);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("day", route.day().dayNumber());
        view.put("date", route.day().date().toString());
        view.put("zone", route.day().zone().getLabel());
        view.put("window", window.isEmpty() ? "일정 없음" : window.start() + " ~ " + window.end());
        view.put("stops", stops);
        view.put("lunch", mealView(meals.lunch()));
        view.put("dinner", mealView(meals.dinner()));
        view.put("night", route.night() == null ? null : route.night().attraction().getName());
        view.put("endLeg", route.endLeg() == null ? null : legView(route.endLeg()));
        view.put("travelMinutes", route.travelMinutes());
        view.put("spares", route.spares().stream().map(s -> s.attraction().getName()).toList());
        return view;
    }

    private Object mealView(PlannedMeal meal) {
        if (meal == null) return "시간대상 없음";
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("anchor", meal.anchor().name() + " (" + round4(meal.anchor().lat()) + ", " + round4(meal.anchor().lng()) + ")");
        if (!meal.found()) {
            view.put("chosen", "후보 없음");
            return view;
        }
        view.put("chosen", restaurantView(meal.chosen(), meal.meal()));
        view.put("genreRepeated", meal.genreRepeated());
        view.put("alternatives", meal.alternatives().stream()
                .map(c -> c.restaurant().getName() + " (" + c.restaurant().getGenre() + ")")
                .toList());
        return view;
    }

    private static Map<String, Object> restaurantView(RestaurantCandidate c, Meal meal) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", c.restaurant().getName());
        view.put("genre", c.restaurant().getGenre());
        view.put("rating", c.restaurant().getRatingVal());
        view.put("budget", meal == Meal.LUNCH ? c.restaurant().getBudgetLunch() : c.restaurant().getBudgetDinner());
        view.put("distanceMeters", c.distanceMeters());
        view.put("budgetRelaxed", c.budgetRelaxed());
        return view;
    }

    private static double round4(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    private static String legView(TravelLeg leg) {
        return leg.mode() + " " + leg.minutes() + "분 (" + leg.distanceMeters() + "m)";
    }

    private Map<String, Object> attractionView(ScoredAttraction c) {
        return Map.of(
                "name", c.attraction().getName(),
                "score", round2(c.score()),
                "rating", c.attraction().getRating(),
                "reviews", c.attraction().getReviewCnt(),
                "labels", typeLabels.toVisibleLabels(c.attraction().getTypes()));
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}