package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.meal.*;
import com.example.japantravelplanner.itinerary.narration.ItineraryNarrator;
import com.example.japantravelplanner.itinerary.narration.TripBrief;
import com.example.japantravelplanner.itinerary.planner.*;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import com.example.japantravelplanner.trip.service.TripAccess;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 일정 생성 단계별 확인용 (저장하지 않는다). 일정 계산은 ItineraryPlanner를 그대로 쓴다.
 * 저장까지 하는 실제 흐름은 ItineraryGenerationService. 화면 연결이 끝나면 이 클래스와 확인용 컨트롤러는 삭제
 */
@Service
@RequiredArgsConstructor
public class ItineraryPreviewService {

    private final TripRepository tripRepository;
    private final TripPreferenceRepository preferenceRepository;
    private final AccommodationLocator accommodationLocator;
    private final ItineraryPlanner planner;
    private final ItineraryNarrator narrator;
    private final PlaceTypeLabels typeLabels;
    private final MealBudgetCalculator mealBudgetCalculator;
    private final FoodFilterResolver foodFilterResolver;
    private final RestaurantFinder restaurantFinder;

    /** ②③ 날짜별 지역과 관광지 후보 */
    @Transactional
    public List<Map<String, Object>> previewCandidates(Long userId, Long tripId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        List<PreferenceConstraint> constraints = constraintsOf(tripId);
        Point hotel = accommodationLocator.locate(trip);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DayZone day : planner.planZones(trip, constraints, hotel)) {
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
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);

        double foodRatio = preference == null ? 0.4 : preference.getFoodBudgetRatio();
        MealBudget budget = mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(),
                ItineraryPlanner.daysOf(trip), foodRatio);
        FoodFilter filter = foodFilterResolver.resolve(ItineraryPlanner.constraintsOf(preference), trip.getAllergies());

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

    /**
     * 전체 일정 시각표. narrate가 true면 Terra 하루 설명까지
     * (확인용이라 AI 호출이 트랜잭션 안에 있다. 실제 생성 Service는 트랜잭션 밖에서 호출한다)
     */
    @Transactional
    public Map<String, Object> previewRoutes(Long userId, Long tripId, boolean narrate) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);
        Point hotel = accommodationLocator.locate(trip);

        ItineraryPlanner.Result plan = planner.plan(trip, preference, hotel);

        Map<Integer, String> descriptions = narrate
                ? narrator.narrate(new TripBrief(trip.getTravelers(), trip.getTravelPace(),
                        List.copyOf(ItineraryPlanner.interestsOf(trip)),
                        List.copyOf(ItineraryPlanner.constraintsOf(preference))),
                plan.days(), typeLabels::toVisibleLabels)
                : Map.of();

        List<Map<String, Object>> dayViews = new ArrayList<>();
        for (int i = 0; i < plan.days().size(); i++) {
            ScheduledDay scheduled = plan.days().get(i);
            dayViews.add(scheduleView(scheduled, plan.windows().get(i), descriptions.get(scheduled.dayNumber())));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", plan.options().mode());
        result.put("walkThresholdMinutes", plan.options().walkThresholdMinutes());
        result.put("nightView", plan.nightView());
        result.put("mealBudget", Map.of("lunchMaxYen", plan.budget().lunchMaxYen(), "dinnerMaxYen", plan.budget().dinnerMaxYen()));
        result.put("days", dayViews);
        return result;
    }

    // ── 공통 ──


    private List<PreferenceConstraint> constraintsOf(Long tripId) {
        return ItineraryPlanner.constraintsOf(preferenceRepository.findByTripId(tripId).orElse(null));
    }

    // ── 응답 모양 (확인용) ──

    private Map<String, Object> scheduleView(ScheduledDay day, DayWindow window, String description) {
        List<Map<String, Object>> stops = new ArrayList<>();
        for (ScheduledStop stop : day.stops()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("time", stop.arrival() + " ~ " + stop.departure());
            view.put("type", stop.type());
            view.put("name", stop.name());
            view.put("stayMinutes", stop.stayMinutes());
            if (stop.waitMinutes() > 0) {
                view.put("waitMinutes", stop.waitMinutes());   // 식사 시간대를 기다린 시간 ("여유 시간")
            }
            view.put("travelIn", legView(stop.travelIn()));

            if (stop.type() == StopType.ATTRACTION) {
                view.put("labels", typeLabels.toVisibleLabels(stop.attraction().attraction().getTypes()));
            } else {
                PlannedMeal meal = stop.meal();
                view.put("restaurant", restaurantView(meal.chosen(), meal.meal()));
                view.put("genreRepeated", meal.genreRepeated());
                view.put("alternatives", meal.alternatives().stream()
                        .map(c -> c.restaurant().getName() + " (" + c.restaurant().getGenre() + ")")
                        .toList());
            }
            stops.add(view);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("day", day.dayNumber());
        view.put("date", day.date().toString());
        view.put("zone", day.zone().getLabel());
        view.put("window", window.isEmpty() ? "일정 없음" : window.start() + " ~ " + window.end());
        if (description != null) {
            view.put("description", description);   // Terra가 쓴 하루 설명 (없을 수 있음)
        }
        view.put("stops", stops);
        view.put("dropped", day.dropped().stream()
                .map(d -> d.name() + " (" + d.reason().label() + ")")
                .toList());
        view.put("warnings", day.warnings());
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