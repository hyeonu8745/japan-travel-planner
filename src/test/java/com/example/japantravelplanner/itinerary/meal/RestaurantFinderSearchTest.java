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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 반경을 넓혀가며 후보를 찾는 규칙 (저장소는 가짜) */
class RestaurantFinderSearchTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final FoodFilter NO_FILTER = new FoodFilter(Set.of(), Set.of(), false);

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantFinder finder = new RestaurantFinder(repository, new MealProperties());

    private static Restaurant restaurant(long id, String name, double rating) {
        return Restaurant.builder().id(id).name(name).genre("洋食").ratingVal(rating).reviewCnt(100)
                .budgetDinnerMax(3000).location(GF.createPoint(new Coordinate(139.70, 35.69))).build();
    }

    private final Restaurant a = restaurant(1, "가까운집", 3.48);
    private final Restaurant b = restaurant(2, "둘", 3.70);
    private final Restaurant c = restaurant(3, "셋", 3.65);
    private final Restaurant d = restaurant(4, "넷", 3.60);

    private List<RestaurantCandidate> find() {
        return finder.find(35.69, 139.70, Meal.DINNER, 5700, NO_FILTER, Set.of(), 10);
    }

    @Test
    void 가까운_후보가_한_곳뿐이면_더_넓은_반경까지_찾는다() {
        when(repository.findWithinRadius(
                anyDouble(), anyDouble(), eq(500.0)
        )).thenReturn(List.of(a));

        when(repository.findWithinRadius(
                anyDouble(), anyDouble(), eq(1000.0)
        )).thenReturn(List.of(a, b, c, d));

        List<RestaurantCandidate> result = find();

        assertThat(result).hasSize(4);
        assertThat(result)
                .extracting(r -> r.restaurant().getName())
                .contains("둘", "셋", "넷", "가까운집");
    }

    @Test
    void 후보가_충분하면_더_넓히지_않는다() {
        when(repository.findWithinRadius(
                anyDouble(), anyDouble(), eq(500.0)
        )).thenReturn(List.of(a, b, c));

        List<RestaurantCandidate> result = find();

        assertThat(result).hasSize(3);

        verify(repository, never()).findWithinRadius(
                anyDouble(), anyDouble(), eq(1000.0)
        );
    }

    @Test
    void 끝까지_넓혀도_한_곳뿐이면_그_한_곳을_돌려준다() {
        when(repository.findWithinRadius(
                anyDouble(), anyDouble(), anyDouble()
        )).thenReturn(List.of(a));

        List<RestaurantCandidate> result = find();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).budgetRelaxed()).isFalse();   // 예산을 올린 게 아니라 반경만 넓힌 결과
    }

    @Test
    void 넓은_반경에서도_가까운_곳은_거리_감점_덕에_계속_유리하다() {
        // 평점이 같으면 가까운 곳이 위
        Restaurant near = restaurant(5, "가까움", 3.60);
        Restaurant far = Restaurant.builder().id(6L).name("멂").genre("洋食").ratingVal(3.60).reviewCnt(100)
                .budgetDinnerMax(3000).location(GF.createPoint(new Coordinate(139.70, 35.70))).build();   // 약 1.1km
        when(repository.findWithinRadius(
                anyDouble(), anyDouble(), eq(500.0)
        )).thenReturn(List.of());

        when(repository.findWithinRadius(
                anyDouble(), anyDouble(), eq(1000.0)
        )).thenReturn(List.of(far));

        when(repository.findWithinRadius(
                anyDouble(), anyDouble(), eq(1500.0)
        )).thenReturn(List.of(near, far));

        List<RestaurantCandidate> result = find();

        assertThat(result.get(0).restaurant().getName()).isEqualTo("가까움");
    }
}