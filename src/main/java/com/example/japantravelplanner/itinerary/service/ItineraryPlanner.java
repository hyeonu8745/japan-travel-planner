package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.BrandKey;
import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.DayZonePlanner;
import com.example.japantravelplanner.itinerary.candidate.FullDayParks;
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
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.DayTripRoute;
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
    private final CafePlanner cafePlanner;
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

    /** 계산 기록(trace) 없이 일정 계산 */
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
        TravelPoint hotelPoint = TravelPoint.of(trip.getAccommodationName(), hotel);

        double foodRatio = preference == null ? DEFAULT_FOOD_RATIO : preference.getFoodBudgetRatio();
        MealBudget budget = krwPerYen == null
                ? mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), days, foodRatio)
                : mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(), days, foodRatio, krwPerYen);
        FoodFilter filter = foodFilterResolver.resolve(constraints, trip.getAllergies());
        MealHistory history = new MealHistory(trip.excludedRestaurants());   // 사용자가 바꾼 식당은 다시 넣지 않는다

        Zones planned = planZones(trip, constraints, hotel, options.compact());
        List<DayZone> zones = planned.days();
        Integer flightStart = AirportPlan.firstDayStart(trip);   // 비행기 시각이 있으면 공항 이동까지 계산
        Integer flightEnd = AirportPlan.lastDayEnd(trip);
        List<DayWindow> windows = new ArrayList<>();
        for (DayZone day : zones) {
            DayWindow window = DayWindow.of(day.dayNumber() == 1, day.dayNumber() == days,
                    preference == null ? null : preference.getArrivalTime(),
                    preference == null ? null : preference.getDepartureTime(),
                    nightView, flightStart, flightEnd);
            // 당일치기 날: 아침에 숙소를 나서 이동한 만큼 늦게 시작하고, 돌아올 시간만큼 일찍 끝낸다
            DayWindow dayTripWindow = window;
            window = DayTrips.routeOf(trip, day.zone()).map(r -> DayTrips.window(dayTripWindow, r)).orElse(window);
            windows.add(window);
        }

        // 야경 장소는 날짜마다 따로 고르지 않고 여행 전체를 보고 나눈다 (전망대만 반복되지 않게)
        // 당일치기 날은 돌아올 시간 때문에 야경을 넣지 않는다 (그날은 야경 배정에서 뺀다)
        List<DayWindow> nightWindows = new ArrayList<>();
        for (int i = 0; i < zones.size(); i++) {
            nightWindows.add(DayTrips.routeOf(trip, zones.get(i).zone()).isPresent()
                    ? new DayWindow(null, null, false, false) : windows.get(i));
        }
        Map<Integer, ScoredAttraction> nights = nightView ? NightSpotPlanner.assign(zones, planned.scored(), nightWindows) : Map.of();
        Set<Long> nightIds = new HashSet<>();
        nights.values().forEach(n -> nightIds.add(n.attraction().getId()));

        List<ScheduledDay> scheduledDays = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();      // 앞 날짜에 실제로 들어간 관광지
        Set<String> usedBrands = new HashSet<>(); // 그 관광지의 가게 이름 (다른 지점도 빼려고, BrandKey)
        for (int i = 0; i < zones.size(); i++) {
            // 앞 날짜에 이미 들어간 관광지는 후보에서 뺀다.
            // (하루 두 지역이라 어떤 날의 예비 후보(이웃 지역)가 다른 날의 후보와 겹칠 수 있다)
            DayZone day = withoutUsed(zones.get(i), usedIds, usedBrands);
            DayWindow window = windows.get(i);
            // 하루를 쓰는 테마파크(USJ 등)가 그날 방문 후보에 있으면 그날은 그곳만, 점심은 파크 안에서
            day = FullDayParks.shape(day, trip.getTravelPace().attractionsPerDay(), window, days);
            boolean parkDay = FullDayParks.isParkDay(day);
            if (parkDay) {
                window = new DayWindow(window.start(), window.end(), false, window.dinner());
                windows.set(i, window);
            }
            // 당일치기 날은 그 도시 도착역에서 시작해서 도착역으로 돌아온다
            TravelPoint start = DayTrips.startPoint(trip, day.zone(), hotelPoint);
            var dayTrip = DayTrips.routeOf(trip, day.zone());
            // 하루 관광지 수는 여행 속도의 수 (야경 포함), 당일치기 날은 2~3곳. 저녁 전 빈 시간을 관광지로 채우지 않는다 (11-22)
            int limit = dayTrip.isPresent() ? trip.getTravelPace().dayTripAttractions() : trip.getTravelPace().attractionsPerDay();
            DayRoute route = routeOptimizer.optimize(day, start, trip.getTravelPace(), options,
                    nights.get(day.dayNumber()), nightIds, limit, false);
            DayMeals meals = mealPlanner.plan(route, start, window, budget, filter, history);
            ScheduledDay scheduled = dayScheduler.schedule(route, meals, window, start, options,
                    trip.getTravelPace().getStayMultiplier());
            // 당일치기 날 관광이 일찍 끝나 저녁까지 한참 기다리면 그 도시 저녁은 빼고 돌아간다
            if (dayTrip.isPresent() && window.dinner() && DayTrips.longDinnerWait(scheduled)) {
                window = new DayWindow(window.start(), window.end(), window.lunch(), false);
                windows.set(i, window);
                meals = new DayMeals(meals.lunch(), null);   // 점심은 그대로 (다시 찾으면 같은 가게가 '이미 쓴 곳'이 된다)
                scheduled = dayScheduler.schedule(route, meals, window, start, options,
                        trip.getTravelPace().getStayMultiplier());
            }
            // 오후 쉬는 시간 카페 한 곳 (시간이 남는 날만, 같은 가게는 여행 전체에서 한 번)
            scheduled = cafePlanner.addBreak(scheduled, window, start, options, filter, history.usedIds());
            if (parkDay) scheduled = withNote(scheduled, FullDayParks.NOTE);
            if (dayTrip.isPresent()) {
                scheduled = withNote(scheduled, DayTrips.note(trip.getBaseCity(), day.zone().getCity(), dayTrip.get(), window.dinner()));
            }
            if (i == 0) {
                for (String skipped : planned.assignment().skipped()) scheduled = withNote(scheduled, skipped);
            }
            scheduled.stops().stream().filter(stop -> stop.type() == StopType.CAFE)
                    .forEach(stop -> history.recordUsedOnly(stop.meal().chosen().restaurant().getId()));
            scheduled.stops().stream().filter(stop -> stop.attraction() != null)
                    .forEach(stop -> {
                        usedIds.add(stop.attraction().attraction().getId());
                        usedBrands.add(BrandKey.of(stop.attraction().attraction().getName()));
                    });
            scheduledDays.add(scheduled);
        }
        return new Result(options, nightView, budget, scheduledDays, windows);
    }

    /** 그날 안내 문장을 하나 더 붙인다 */
    static ScheduledDay withNote(ScheduledDay day, String note) {
        List<String> warnings = new ArrayList<>(day.warnings());
        warnings.add(note);
        return new ScheduledDay(day.dayNumber(), day.date(), day.zone(), day.stops(), day.dropped(), List.copyOf(warnings));
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

    /**
     * 날짜별 지역과 관광지 후보
     * @param scored     모든 날의 후보 도시(숙소 도시 + 당일치기 도시) 관광지 점수 (야경 배정용)
     * @param assignment 당일치기 날 배정 (넣지 못한 도시 안내 포함)
     */
    record Zones(List<DayZone> days, List<ScoredAttraction> scored, DayTrips.Assignment assignment) {
    }

    /** 날짜별 지역과 관광지 후보 (②③ 단계) */
    public List<DayZone> planZones(Trip trip, List<PreferenceConstraint> constraints, Point hotel) {
        boolean compact = TravelOptions.of(trip, constraints, travelProperties).compact();
        return planZones(trip, constraints, hotel, compact).days();
    }

    /**
     * 날짜별 지역과 지역마다 관광지 후보.
     * 숙소 도시 날은 지금까지처럼 한 번에 배정하고(첫날·마지막 날 숙소 근처 규칙 그대로),
     * 당일치기 날은 그 도시 관광지로 하루짜리 배정을 따로 해서 그 날 번호에 끼운다 (도착역을 숙소처럼 본다)
     */
    private Zones planZones(Trip trip, List<PreferenceConstraint> constraints, Point hotel, boolean compact) {
        List<com.example.japantravelplanner.attraction.entity.Attraction> all = attractionRepository.findAll();
        List<Interest> interests = interestsOf(trip);
        java.util.Set<Wish> wishes = Wish.from(constraints);
        List<ScoredAttraction> scored = scorer.score(all, interests, wishes, trip.getBaseCity());
        int days = daysOf(trip);
        int perDay = trip.getTravelPace().attractionsPerDay();
        double range = dayZonePlanner.rangeMeters(compact, trip.getTransportMode() == TransportMode.RENTAL_CAR);

        DayTrips.Assignment assignment = DayTrips.assign(trip, days);
        Map<City, List<ScoredAttraction>> byCity = new java.util.EnumMap<>(City.class);
        for (City city : assignment.byDay().values()) {
            byCity.put(city, scorer.score(all, interests, wishes, city));
        }

        List<DayZone> base = dayZonePlanner.plan(scored, trip.getStartDate(), days - assignment.count(), perDay,
                interests, constraints, hotel.getY(), hotel.getX(), compact, range,
                trip.departureAirportInfo().isPresent());   // 비행기로 떠나는 마지막 날은 숙소 근처 쇼핑

        List<DayZone> result = new ArrayList<>();
        List<ScoredAttraction> allScored = new ArrayList<>(scored);
        int next = 0;
        for (int d = 1; d <= days; d++) {
            java.time.LocalDate date = trip.getStartDate().plusDays(d - 1);
            City city = assignment.byDay().get(d);
            List<ScoredAttraction> cityScored = city == null ? List.of() : byCity.get(city);
            DayZone zone;
            if (city == null || cityScored.isEmpty()) {
                zone = next < base.size() ? base.get(next++) : null;
            } else {
                TravelPoint station = DayTrips.station(DayTripRoute.between(trip.getBaseCity(), city).orElseThrow(), city);
                zone = dayZonePlanner.plan(cityScored, date, 1, perDay, interests, constraints,
                        station.lat(), station.lng(), compact, range, false).get(0);
                allScored.addAll(cityScored);
            }
            if (zone == null) continue;   // 당일치기 도시에 관광지가 없어 숙소 도시 날이 모자란 경우 (데이터상 없음)
            result.add(new DayZone(d, date, zone.zone(), zone.candidates(), zone.partner()));
        }
        return new Zones(result, allScored, assignment);
    }

    /** 취향 정리본의 조건 (없으면 빈 목록) */
    public static List<PreferenceConstraint> constraintsOf(TripPreference preference) {
        return preference == null ? List.of() : preference.getConstraints();
    }

    /** 여행 관심사 (없으면 빈 목록) */
    public static List<Interest> interestsOf(Trip trip) {
        return trip.getInterests() == null ? List.of() : trip.getInterests();
    }

    /** 여행 날 수 */
    public static int daysOf(Trip trip) {
        return (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }
}
