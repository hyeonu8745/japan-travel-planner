package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.BrandKey;
import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.DayZonePlanner;
import com.example.japantravelplanner.itinerary.candidate.NightSpotPlanner;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.candidate.Wish;
import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.itinerary.meal.FoodFilterResolver;
import com.example.japantravelplanner.itinerary.meal.MealBudget;
import com.example.japantravelplanner.itinerary.meal.MealBudgetCalculator;
import com.example.japantravelplanner.itinerary.planner.*;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 일정을 계산하는 파이프라인 전체: 지역 배정 → 관광지 후보 → 야경 장소(여행 전체) → 순서(TSP) → 점심·저녁 → 시각.
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
        return plan(trip, preference, hotel, null);
    }

    /** @param krwPerYen 이번 생성에 쓸 환율 (null이면 설정 고정값) */
    public Result plan(Trip trip, TripPreference preference, Point hotel, Double krwPerYen) {
        List<PreferenceConstraint> constraints = constraintsOf(preference);
        List<Interest> interests = interestsOf(trip);
        boolean nightView = interests.contains(Interest.NIGHT_VIEW);
        int days = daysOf(trip);

        TravelOptions options = TravelOptions.of(trip, constraints, travelProperties);
        TravelPoint hotelPoint = new TravelPoint(trip.getAccommodationName(), hotel.getY(), hotel.getX());

        double foodRatio = preference == null ? DEFAULT_FOOD_RATIO : preference.getFoodBudgetRatio();
        MealBudget budget = krwPerYen == null
                ? mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), days, foodRatio)
                : mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), days, foodRatio, krwPerYen);
        FoodFilter filter = foodFilterResolver.resolve(constraints, trip.getAllergies());
        MealHistory history = new MealHistory();

        List<ScoredAttraction> scored = scoredAttractions(trip, constraints);
        List<DayZone> zones = planZones(trip, constraints, hotel, scored, options.compact());
        List<DayWindow> windows = new ArrayList<>();
        for (DayZone day : zones) {
            windows.add(DayWindow.of(day.dayNumber() == 1, day.dayNumber() == days,
                    preference == null ? null : preference.getArrivalTime(),
                    preference == null ? null : preference.getDepartureTime(),
                    nightView));
        }

        // 야경 장소는 날짜마다 따로 고르지 않고 여행 전체를 보고 나눈다 (전망대만 반복되지 않게)
        Map<Integer, ScoredAttraction> nights = nightView ? NightSpotPlanner.assign(zones, scored, windows) : Map.of();
        Set<Long> nightIds = new HashSet<>();
        nights.values().forEach(n -> nightIds.add(n.attraction().getId()));

        List<ScheduledDay> scheduledDays = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();      // 앞 날짜에 실제로 들어간 관광지
        Set<String> usedBrands = new HashSet<>(); // 그 관광지의 가게 이름 (다른 지점도 빼려고, BrandKey)
        for (int i = 0; i < zones.size(); i++) {
            // 앞 날짜에 이미 들어간 관광지는 후보에서 뺀다.
            // 10-11: 하루 두 지역이 되면서 어떤 날의 예비 후보(이웃 지역)가 다른 날의 후보와 겹쳐,
            // 예비로 채워진 忍者体験カフェ原宿가 3·5·6일차에 모두 들어갔다 (여행 8 v12)
            DayZone day = withoutUsed(zones.get(i), usedIds, usedBrands);
            DayWindow window = windows.get(i);
            DayRoute route = routeOptimizer.optimize(day, hotelPoint, trip.getTravelPace(), options,
                    nights.get(day.dayNumber()), nightIds);
            DayMeals meals = mealPlanner.plan(route, hotelPoint, window, budget, filter, history);
            ScheduledDay scheduled = dayScheduler.schedule(route, meals, window, hotelPoint, options,
                    trip.getTravelPace().getStayMultiplier());
            scheduled.stops().stream().filter(stop -> stop.attraction() != null)
                    .forEach(stop -> {
                        usedIds.add(stop.attraction().attraction().getId());
                        usedBrands.add(BrandKey.of(stop.attraction().attraction().getName()));
                    });
            scheduledDays.add(scheduled);
        }
        return new Result(options, nightView, budget, scheduledDays, windows);
    }

    /** 앞 날짜에 들어간 관광지와 같은 가게의 다른 지점(忍者体験カフェ原宿 ↔ 浅草)을 후보에서 뺀다 */
    static DayZone withoutUsed(DayZone day, Set<Long> usedIds, Set<String> usedBrands) {
        if (usedIds.isEmpty()) return day;
        return new DayZone(day.dayNumber(), day.date(), day.zone(),
                day.candidates().stream()
                        .filter(c -> !usedIds.contains(c.attraction().getId()))
                        .filter(c -> !usedBrands.contains(BrandKey.of(c.attraction().getName())))
                        .toList(),
                day.partner());
    }

    /** 날짜별 지역과 관광지 후보 (②③ 단계) */
    public List<DayZone> planZones(Trip trip, List<PreferenceConstraint> constraints, Point hotel) {
        boolean compact = TravelOptions.of(trip, constraints, travelProperties).compact();
        return planZones(trip, constraints, hotel, scoredAttractions(trip, constraints), compact);
    }

    private List<ScoredAttraction> scoredAttractions(Trip trip, List<PreferenceConstraint> constraints) {
        return scorer.score(attractionRepository.findAll(), interestsOf(trip), Wish.from(constraints));
    }

    private List<DayZone> planZones(Trip trip, List<PreferenceConstraint> constraints, Point hotel,
                                    List<ScoredAttraction> scored, boolean compact) {
        double range = dayZonePlanner.rangeMeters(compact, trip.getTransportMode() == TransportMode.RENTAL_CAR);
        return dayZonePlanner.plan(scored, trip.getStartDate(), daysOf(trip),
                trip.getTravelPace().attractionsPerDay(), interestsOf(trip), constraints,
                hotel.getY(), hotel.getX(), compact, range);
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