package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 방문하는 날이 정기 휴일인 식당은 일정에 넣지 않는다 */
class RestaurantFinderHolidayTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final FoodFilter NO_FILTER = new FoodFilter(Set.of(), Set.of(), false);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);   // 2026-10-23은 금요일

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantFinder finder = new RestaurantFinder(repository, new MealProperties());

    private static Restaurant restaurant(long id, String name, String holiday) {
        return Restaurant.builder().id(id).name(name).genre("洋食").ratingVal(3.8).reviewCnt(100)
                .budgetDinnerMax(3000).budgetLunchMax(1500).holiday(holiday)
                .location(GF.createPoint(new Coordinate(139.70 + id / 1000.0, 35.69))).build();
    }

    private void repositoryReturns(Restaurant... restaurants) {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(restaurants));
    }

    private List<String> names(LocalDate visitDate) {
        return finder.find(35.69, 139.70, Meal.DINNER, 5700, NO_FILTER, Set.of(), 10, visitDate).stream()
                .map(c -> c.restaurant().getName()).toList();
    }

    @Test
    void 방문하는_날이_정기_휴일이면_후보에서_뺀다() {
        // 실제 사례: DAY4(금요일) 점심 スパイスポスト는 月曜日、金曜日 휴무였다
        repositoryReturns(restaurant(1, "금요일 휴무", "月曜日、金曜日"), restaurant(2, "연중무휴", "無休"),
                restaurant(3, "일요일 휴무", "日曜日"), restaurant(4, "휴일 정보 없음", null));

        assertThat(names(FRIDAY)).containsExactlyInAnyOrder("연중무휴", "일요일 휴무", "휴일 정보 없음");
    }

    @Test
    void 방문_날짜를_모르면_휴일을_따지지_않는다() {
        repositoryReturns(restaurant(1, "금요일 휴무", "月曜日、金曜日"), restaurant(2, "연중무휴", "無休"));

        assertThat(names(null)).containsExactlyInAnyOrder("금요일 휴무", "연중무휴");
    }

    @Test
    void 날짜_없는_기존_호출도_휴일을_따지지_않는다() {
        repositoryReturns(restaurant(1, "금요일 휴무", "月曜日、金曜日"));

        List<RestaurantCandidate> found = finder.find(35.69, 139.70, Meal.DINNER, 5700, NO_FILTER, Set.of(), 10);

        assertThat(found).hasSize(1);
    }

    @Test
    void 문구가_애매하면_휴일로_보지_않고_후보에_남긴다() {
        repositoryReturns(restaurant(1, "비정기 휴무", "不定休"), restaurant(2, "둘째 주 휴무", "第2・第4月曜日"),
                restaurant(3, "공휴일만 휴무", "祝日"));

        assertThat(names(FRIDAY)).containsExactlyInAnyOrder("비정기 휴무", "둘째 주 휴무", "공휴일만 휴무");
    }

    @Test
    void 휴일_식당을_빼고_나면_후보가_없을_수_있다() {
        repositoryReturns(restaurant(1, "금요일 휴무", "金曜日"));

        assertThat(names(FRIDAY)).isEmpty();
    }
}