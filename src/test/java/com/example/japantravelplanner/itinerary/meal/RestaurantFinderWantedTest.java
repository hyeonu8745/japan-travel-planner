package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 원하는 장르만 넓게 찾기 (채팅 수정 "2일차 저녁을 스시로", 10-11).
 * 여행 8 v12: 보통 검색은 500m 안에서 3곳을 찾으면 멈추고 예산을 넘는 곳은 빼서 스시집이 하나도 안 나왔다.
 */
class RestaurantFinderWantedTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final FoodFilter NO_FILTER = new FoodFilter(Set.of(), Set.of(), false);
    private static final Set<String> SUSHI = Set.of("寿司");

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantFinder finder = new RestaurantFinder(repository, new MealProperties());

    private static Restaurant restaurant(long id, String name, String genre, int dinnerMax) {
        return Restaurant.builder().id(id).name(name).genre(genre).ratingVal(3.6).reviewCnt(200)
                .budgetDinnerMax(dinnerMax).location(GF.createPoint(new Coordinate(139.745, 35.663))).build();
    }

    private final Restaurant pizza = restaurant(1, "피자집", "ピザ,イタリアン", 3000);
    private final Restaurant soba = restaurant(2, "소바집", "そば", 3000);
    private final Restaurant cafe = restaurant(3, "카페", "カフェ", 2000);

    @Test
    void 가까운_곳에_없으면_넓은_반경에서_원하는_장르만_찾는다() {
        Restaurant sushi = restaurant(10, "스시집", "寿司,海鮮丼", 5000);
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(500.0))).thenReturn(List.of(pizza, soba, cafe));
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(1000.0))).thenReturn(List.of(pizza, soba, cafe, sushi));

        List<RestaurantCandidate> found = finder.findWanted(35.663, 139.745, Meal.DINNER, 5700, NO_FILTER,
                Set.of(), 10, SUSHI);

        assertThat(found).extracting(c -> c.restaurant().getName()).containsExactly("스시집");
        assertThat(found.get(0).budgetRelaxed()).isFalse();
    }

    @Test
    void 예산_안에_없으면_예산을_올려서_찾고_표시한다() {
        Restaurant pricey = restaurant(11, "비싼 스시집", "寿司", 7500);   // 5700엔의 1.4배(7980) 안
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(pizza, pricey));

        List<RestaurantCandidate> found = finder.findWanted(35.663, 139.745, Meal.DINNER, 5700, NO_FILTER,
                Set.of(), 10, SUSHI);

        assertThat(found).extracting(c -> c.restaurant().getName()).containsExactly("비싼 스시집");
        assertThat(found.get(0).budgetRelaxed()).isTrue();
    }

    @Test
    void 보통_반경_1500m_안에_없으면_3km까지_넓혀서_찾는다() {
        // 여행 9: "5일차 점심 장어덮밥" → 진구마에 근처 1.5km 안에 장어집이 없어 카레집만 나왔다
        Restaurant unagi = restaurant(12, "장어집", "うなぎ", 5000);
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(3000.0))).thenReturn(List.of(pizza, unagi));

        List<RestaurantCandidate> found = finder.findWanted(35.663, 139.745, Meal.DINNER, 5700, NO_FILTER,
                Set.of(), 10, Set.of("うなぎ"));

        assertThat(found).extracting(c -> c.restaurant().getName()).containsExactly("장어집");
        verify(repository).findWithinRadius(anyDouble(), anyDouble(), eq(2000.0));
    }

    @Test
    void 가까운_곳에서_예산을_조금_넘는_곳이_있으면_더_멀리_가지_않는다() {
        Restaurant near = restaurant(13, "가까운 장어집", "うなぎ", 7500);   // 1.4배 안
        Restaurant far = restaurant(14, "먼 장어집", "うなぎ", 5000);
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(1500.0))).thenReturn(List.of(near));
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(2000.0))).thenReturn(List.of(near, far));

        List<RestaurantCandidate> found = finder.findWanted(35.663, 139.745, Meal.DINNER, 5700, NO_FILTER,
                Set.of(), 10, Set.of("うなぎ"));

        assertThat(found).extracting(c -> c.restaurant().getName()).containsExactly("가까운 장어집");
        verify(repository, never()).findWithinRadius(anyDouble(), anyDouble(), eq(2000.0));
    }

    @Test
    void 원하는_장르가_없거나_비어_있으면_빈_목록() {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(pizza, soba));

        assertThat(finder.findWanted(35.663, 139.745, Meal.DINNER, 5700, NO_FILTER, Set.of(), 10, SUSHI)).isEmpty();
        assertThat(finder.findWanted(35.663, 139.745, Meal.DINNER, 5700, NO_FILTER, Set.of(), 10, Set.of())).isEmpty();
    }
}
