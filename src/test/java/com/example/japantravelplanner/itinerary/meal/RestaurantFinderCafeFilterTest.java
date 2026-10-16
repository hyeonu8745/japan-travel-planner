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

/** 10-16: 쉬는 시간 카페에도 끼니와 같은 음식 조건(피할 음식·알레르기)을 적용한다 */
class RestaurantFinderCafeFilterTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantFinder finder = new RestaurantFinder(repository, new MealProperties());

    private static Restaurant cafe(long id, String genre) {
        return Restaurant.builder().id(id).name("카페" + id).genre(genre).ratingVal(3.6)
                .location(GF.createPoint(new Coordinate(139.70, 35.69))).build();
    }

    private List<Long> ids(FoodFilter filter) {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(cafe(1, "喫茶店,カフェ"), cafe(2, "ケーキ,カフェ"), cafe(3, "パンケーキ")));
        return finder.findCafe(35.69, 139.70, filter, Set.of(), LocalDate.of(2026, 10, 20),
                        List.of(300), 2500, 0.5, 5).stream().map(c -> c.restaurant().getId()).sorted().toList();
    }

    @Test
    void 조건이_없으면_카페_디저트_모두_후보() {
        assertThat(ids(new FoodFilter(Set.of(), Set.of(), false))).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 밀가루_알레르기면_케이크_팬케이크집은_빼고_찻집만_남는다() {
        assertThat(ids(new FoodFilter(Set.of(), Set.of("ケーキ", "パンケーキ"), false))).containsExactly(1L);
    }

    @Test
    void 카페를_피하면_카페가_들어간_곳은_모두_빠진다() {
        assertThat(ids(new FoodFilter(Set.of(), Set.of("カフェ", "喫茶店"), false))).containsExactly(3L);
    }
}
