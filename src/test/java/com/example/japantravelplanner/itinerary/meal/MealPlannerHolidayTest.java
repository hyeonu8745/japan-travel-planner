package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.MealBudget;
import com.example.japantravelplanner.itinerary.meal.MealProperties;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 끼니를 고를 때 그날 날짜를 식당 검색에 넘기는지 (정기 휴일 식당을 거르기 위해) */
class MealPlannerHolidayTest {

    private static final TravelPoint HOTEL = new TravelPoint("숙소", 35.70, 139.70);
    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);

    private final RestaurantFinder finder = mock(RestaurantFinder.class);
    private final MealPlanner planner = new MealPlanner(finder, new MealProperties());

    @Test
    void 점심과_저녁_모두_그날_날짜로_찾는다() {
        when(finder.find(anyDouble(), anyDouble(), any(Meal.class), anyInt(), any(FoodFilter.class), any(), anyInt(), any()))
                .thenReturn(List.of());
        DayZone day = new DayZone(4, DATE, TripZone.SHIBUYA_HARAJUKU, List.of());
        DayRoute route = DayRoute.empty(day);
        DayWindow window = new DayWindow(LocalTime.of(9, 30), LocalTime.of(21, 0), true, true);

        planner.plan(route, HOTEL, window, new MealBudget(3100, 5700), new FoodFilter(Set.of(), Set.of(), false),
                new MealHistory());

        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        verify(finder, times(2)).find(anyDouble(), anyDouble(), any(Meal.class), anyInt(), any(FoodFilter.class),
                any(), anyInt(), dates.capture());
        assertThat(dates.getAllValues()).containsExactly(DATE, DATE);
    }

    @Test
    void 끼니가_없는_날은_검색하지_않는다() {
        DayZone day = new DayZone(4, DATE, TripZone.SHIBUYA_HARAJUKU, List.of());
        DayWindow window = new DayWindow(LocalTime.of(9, 30), LocalTime.of(10, 0), false, false);

        planner.plan(DayRoute.empty(day), HOTEL, window, new MealBudget(3100, 5700),
                new FoodFilter(Set.of(), Set.of(), false), new MealHistory());

        verify(finder, times(0)).find(anyDouble(), anyDouble(), eq(Meal.LUNCH), anyInt(), any(FoodFilter.class),
                any(), anyInt(), any());
    }

    @Test
    void 저녁_위치가_숙소에서_멀고_근처에_식당이_없으면_숙소_근처에서_찾는다() {
        // 산 위 전망대(숙소에서 약 6km)에서 끝난 날
        com.example.japantravelplanner.restaurant.entity.Restaurant nearHotel =
                com.example.japantravelplanner.restaurant.entity.Restaurant.builder().id(9L).name("숙소 옆 식당")
                        .genre("洋食").ratingVal(3.6).build();
        when(finder.find(anyDouble(), anyDouble(), any(Meal.class), anyInt(), any(FoodFilter.class), any(), anyInt(), any()))
                .thenReturn(List.of());
        when(finder.find(eq(HOTEL.lat()), eq(HOTEL.lng()), eq(Meal.DINNER), anyInt(), any(FoodFilter.class), any(), anyInt(), any()))
                .thenReturn(List.of(new com.example.japantravelplanner.itinerary.meal.RestaurantCandidate(nearHotel, 200, 3.6, false)));
        com.example.japantravelplanner.itinerary.candidate.ScoredAttraction mountain =
                new com.example.japantravelplanner.itinerary.candidate.ScoredAttraction(
                        com.example.japantravelplanner.attraction.entity.Attraction.builder().id(1L).name("展望台")
                                .location(new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326)
                                        .createPoint(new org.locationtech.jts.geom.Coordinate(139.70, 35.755))).build(),
                        TripZone.SHINJUKU, 5.0);
        DayZone day = new DayZone(2, DATE, TripZone.SHINJUKU, List.of(mountain));
        DayRoute route = new DayRoute(day, List.of(), List.of(mountain), null, List.of(), List.of(), null, 0);
        DayWindow window = new DayWindow(LocalTime.of(9, 30), LocalTime.of(21, 0), false, true);

        DayMeals meals = planner.plan(route, HOTEL, window, new MealBudget(3100, 5700),
                new FoodFilter(Set.of(), Set.of(), false), new MealHistory());

        assertThat(meals.dinner().found()).isTrue();
        assertThat(meals.dinner().chosen().restaurant().getName()).isEqualTo("숙소 옆 식당");
    }
}