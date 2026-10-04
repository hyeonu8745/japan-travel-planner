package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.DayZonePlanner;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.itinerary.meal.FoodFilterResolver;
import com.example.japantravelplanner.itinerary.meal.MealBudget;
import com.example.japantravelplanner.itinerary.meal.MealBudgetCalculator;
import com.example.japantravelplanner.itinerary.planner.*;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 일정을 계산하는 파이프라인 전체: 지역 배정 → 관광지 후보 → 순서(TSP) → 점심·저녁 → 시각.
 * AI 호출과 DB 저장은 하지 않는다. (일정 생성 Service와 확인용 API가 함께 쓴다)
 * 호출하는 쪽의 트랜잭션 안에서 불러야 한다 (여행·취향의 지연 로딩 컬렉션을 읽는다).
 */
@Component
@RequiredArgsConstructor
public class ItineraryPlanner {

    private static final double DEFAULT_FOOD_RATIO = 0.4;

    private final AttractionRepository attractionRepository;
    private final AttractionScorer scorer;
    private final DayZonePlanner dayZonePlanner;
    private final RouteOptimizer routeOptimizer;
    private final MealPlanner mealPlanner;
    private final DayScheduler dayScheduler;
    private final MealBudgetCalculator mealBudgetCalculator;
    private final FoodFilterResolver foodFilterResolver;
    private final TravelProperties travelProperties;

    /**
     * @param days    날짜별 시각표 (빠진 장소와 안내 포함)
     * @param windows days와 같은 순서의 하루 가능 시간대
     */
    public record Result(TravelOptions options, boolean nightView, MealBudget budget,
                         List<ScheduledDay> days, List<DayWindow> windows) {
    }

    public Result plan(Trip trip, TripPreference preference, Point hotel) {
        List<PreferenceConstraint> constraints = constraintsOf(preference);
        List<Interest> interests = interestsOf(trip);
        boolean nightView = interests.contains(Interest.NIGHT_VIEW);
        int days = daysOf(trip);

        TravelOptions options = TravelOptions.of(trip, constraints, travelProperties);
        TravelPoint hotelPoint = new TravelPoint(trip.getAccommodationName(), hotel.getY(), hotel.getX());

        double foodRatio = preference == null ? DEFAULT_FOOD_RATIO : preference.getFoodBudgetRatio();
        MealBudget budget = mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), days, foodRatio);
        FoodFilter filter = foodFilterResolver.resolve(constraints, trip.getAllergies());
        MealHistory history = new MealHistory();

        List<ScheduledDay> scheduledDays = new ArrayList<>();
        List<DayWindow> windows = new ArrayList<>();
        for (DayZone day : planZones(trip, constraints, hotel)) {
            DayWindow window = DayWindow.of(day.dayNumber() == 1, day.dayNumber() == days,
                    preference == null ? null : preference.getArrivalTime(),
                    preference == null ? null : preference.getDepartureTime(),
                    nightView);
            DayRoute route = routeOptimizer.optimize(day, hotelPoint, trip.getTravelPace(), options, nightView);
            DayMeals meals = mealPlanner.plan(route, hotelPoint, window, budget, filter, history);
            scheduledDays.add(dayScheduler.schedule(route, meals, window, hotelPoint, options,
                    trip.getTravelPace().getStayMultiplier()));
            windows.add(window);
        }
        return new Result(options, nightView, budget, scheduledDays, windows);
    }

    /** 날짜별 지역과 관광지 후보 (②③ 단계) */
    public List<DayZone> planZones(Trip trip, List<PreferenceConstraint> constraints, Point hotel) {
        List<Interest> interests = interestsOf(trip);
        List<ScoredAttraction> scored = scorer.score(attractionRepository.findAll(), interests);
        return dayZonePlanner.plan(scored, trip.getStartDate(), daysOf(trip),
                trip.getTravelPace().attractionsPerDay(), interests, constraints,
                hotel.getY(), hotel.getX());
    }

    public static List<PreferenceConstraint> constraintsOf(TripPreference preference) {
        return preference == null ? List.of() : preference.getConstraints();
    }

    public static List<Interest> interestsOf(Trip trip) {
        return trip.getInterests() == null ? List.of() : trip.getInterests();
    }

    public static int daysOf(Trip trip) {
        return (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }
}