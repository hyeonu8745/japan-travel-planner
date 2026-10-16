package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;
import com.example.japantravelplanner.itinerary.planner.*;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 오후 쉬는 시간 카페 (10-16): 점심~저녁 사이 15시에 가까운 자리에 한 곳, 시간이 모자라면 넣지 않는다 */
class CafePlannerTest {

    private final RestaurantFinder finder = mock(RestaurantFinder.class);
    private final TravelTimeService travel = new TravelTimeService(new TravelProperties(), null);
    private final CafeProperties props = new CafeProperties();
    private final CafePlanner planner = new CafePlanner(finder, new DayScheduler(travel), props);
    private final TravelPoint hotel = new TravelPoint("숙소", 35.6951, 139.7020);
    private final TravelOptions options = new TravelOptions(LegMode.TRANSIT, 30, false, null);
    private static final FoodFilter NO_FILTER = new FoodFilter(Set.of(), Set.of(), false);

    private final Restaurant cafe = Restaurant.builder().id(91L).name("喫茶ルノアール").genre("喫茶店,カフェ").ratingVal(3.6)
            .budgetLunch("￥1,000～￥1,999").location(GF.createPoint(new Coordinate(139.7015, 35.6905))).build();
    private final Restaurant other = Restaurant.builder().id(92L).name("パフェ屋").genre("パフェ").ratingVal(3.5)
            .location(GF.createPoint(new Coordinate(139.7030, 35.6910))).build();

    private ScheduledDay fullDay() {
        return day(1, List.of(
                attractionStop(attraction(1, "아침 공원", "park"), "09:40", "10:55", 75),
                mealStop(StopType.LUNCH, restaurant(21, "점심집", "ラーメン"), false, false, "11:30", "12:30", 0),
                attractionStop(attraction(2, "박물관", "museum"), "12:50", "14:35", 105),
                attractionStop(attraction(3, "거리", "tourist_attraction"), "14:55", "15:55", 60),
                mealStop(StopType.DINNER, restaurant(22, "저녁집", "洋食"), false, false, "18:00", "19:30", 0)),
                List.of(), List.of());
    }

    @Test
    void 점심과_저녁_사이_15시에_가장_가까운_자리에_카페를_넣고_시각을_다시_계산한다() {
        when(finder.findCafe(anyDouble(), anyDouble(), any(), any(), any(), any(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(List.of(new RestaurantCandidate(cafe, 120, 3.5, false), new RestaurantCandidate(other, 300, 3.3, false)));

        ScheduledDay result = planner.addBreak(fullDay(), window("09:30", "21:00"), hotel, options, NO_FILTER, Set.of(5L));

        // 박물관을 14:35에 떠남(15:00과 25분 차) / 거리를 15:55에 떠남(55분 차) → 박물관 뒤
        assertThat(result.stops()).extracting(ScheduledStop::type).containsExactly(
                StopType.ATTRACTION, StopType.LUNCH, StopType.ATTRACTION, StopType.CAFE, StopType.ATTRACTION, StopType.DINNER);
        ScheduledStop cafeStop = result.stops().get(3);
        assertThat(cafeStop.name()).isEqualTo("喫茶ルノアール");
        assertThat(cafeStop.stayMinutes()).isEqualTo(40);
        assertThat(cafeStop.meal().meal()).isEqualTo(Meal.CAFE);
        assertThat(cafeStop.meal().alternatives()).extracting(c -> c.restaurant().getName()).containsExactly("パフェ屋");
        assertThat(cafeStop.arrival()).isAfter(LocalTime.of(14, 35));
        assertThat(result.stops().get(4).arrival()).isAfter(cafeStop.departure());   // 뒤 장소는 카페 뒤로 밀림
        assertThat(result.stops().get(5).arrival()).isEqualTo(LocalTime.of(18, 0));   // 저녁은 그대로
        verify(finder).findCafe(anyDouble(), anyDouble(), eq(NO_FILTER), eq(Set.of(5L)), any(), eq(List.of(300, 600, 1000)), eq(2500),
                eq(0.5), eq(3));
    }

    @Test
    void 저녁이_없는_날이나_14시_30분_사이에_떠나는_자리가_없으면_넣지_않는다() {
        ScheduledDay noDinner = day(1, List.of(
                mealStop(StopType.LUNCH, restaurant(21, "점심집", "ラーメン"), false, false, "11:30", "12:30", 0),
                attractionStop(attraction(2, "박물관", "museum"), "12:50", "14:35", 105)), List.of(), List.of());
        assertThat(planner.addBreak(noDinner, window("09:30", "21:00"), hotel, options, NO_FILTER, Set.of())).isSameAs(noDinner);

        ScheduledDay noGap = day(1, List.of(
                mealStop(StopType.LUNCH, restaurant(21, "점심집", "ラーメン"), false, false, "11:30", "12:30", 0),
                attractionStop(attraction(2, "긴 체험", "museum"), "12:50", "17:20", 270),
                mealStop(StopType.DINNER, restaurant(22, "저녁집", "洋食"), false, false, "18:00", "19:30", 0)),
                List.of(), List.of());
        assertThat(planner.addBreak(noGap, window("09:30", "21:00"), hotel, options, NO_FILTER, Set.of())).isSameAs(noGap);
        verifyNoInteractions(finder);
    }

    @Test
    void 근처에_카페가_없거나_넣으면_하루가_넘치면_그대로_둔다() {
        when(finder.findCafe(anyDouble(), anyDouble(), any(), any(), any(), any(), anyInt(), anyDouble(), anyInt())).thenReturn(List.of());
        ScheduledDay day = fullDay();
        assertThat(planner.addBreak(day, window("09:30", "21:00"), hotel, options, NO_FILTER, Set.of())).isSameAs(day);

        when(finder.findCafe(anyDouble(), anyDouble(), any(), any(), any(), any(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(List.of(new RestaurantCandidate(cafe, 120, 3.5, false)));
        // 오후가 꽉 찬 날: 박물관 뒤 긴 체험이 17:35까지, 하루는 19:45까지 → 카페를 넣으면 저녁이 밀려 하루를 넘긴다
        ScheduledDay tight = day(1, List.of(
                mealStop(StopType.LUNCH, restaurant(21, "점심집", "ラーメン"), false, false, "11:30", "12:30", 0),
                attractionStop(attraction(2, "박물관", "museum"), "12:50", "14:35", 105),
                attractionStop(attraction(3, "긴 체험", "amusement_center"), "14:55", "17:35", 160),
                mealStop(StopType.DINNER, restaurant(22, "저녁집", "洋食"), false, false, "18:00", "19:30", 0)),
                List.of(), List.of());
        assertThat(planner.addBreak(tight, window("09:30", "19:45"), hotel, options, NO_FILTER, Set.of())).isSameAs(tight);
    }

    @Test
    void 카페_장르는_대표_장르가_카페_디저트인_곳만() {
        assertThat(RestaurantFinder.isCafeLed("喫茶店,カフェ")).isTrue();
        assertThat(RestaurantFinder.isCafeLed("スイーツ,ケーキ")).isTrue();
        assertThat(RestaurantFinder.isCafeLed("パン,サンドイッチ")).isFalse();     // 사 가는 빵집
        assertThat(RestaurantFinder.isCafeLed("洋食,カフェ")).isFalse();           // 식사가 대표
        assertThat(RestaurantFinder.isCafeLed(null)).isFalse();
    }
}
