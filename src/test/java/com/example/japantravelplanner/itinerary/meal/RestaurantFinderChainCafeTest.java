package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.restaurant.entity.ChainBrand;
import com.example.japantravelplanner.restaurant.entity.ChainCategory;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 11-22: 평점 있는 카페가 근처에 없으면 체인 카페(스타벅스 등)로 쉬는 시간을 넣는다 (소도시) */
class RestaurantFinderChainCafeTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantFinder finder = new RestaurantFinder(repository, new MealProperties());

    private static final ChainBrand STARBUCKS = ChainBrand.builder().id(6L).name("スターバックス").nameKo("스타벅스")
            .category(ChainCategory.CAFE).genreKo("카페").budgetMin(500).budgetMax(1000).build();
    private static final ChainBrand DONUT = ChainBrand.builder().id(34L).name("ミスタードーナツ").nameKo("미스터도넛")
            .category(ChainCategory.DESSERT).genreKo("도넛").budgetMin(300).budgetMax(800).build();
    private static final ChainBrand ANIME = ChainBrand.builder().id(63L).name("アニメイトカフェ").nameKo("애니메이트 카페")
            .category(ChainCategory.CAFE).genreKo("애니 콜라보 카페").budgetMin(1500).budgetMax(3000).build();

    private static Restaurant chain(long id, ChainBrand brand, double lng) {
        return Restaurant.builder().id(id).name(brand.getName()).brand(brand)
                .location(GF.createPoint(new Coordinate(lng, 34.39))).build();
    }

    private static Restaurant rated(long id, double lng) {
        return Restaurant.builder().id(id).name("喫茶" + id).genre("喫茶店,カフェ").ratingVal(3.5)
                .location(GF.createPoint(new Coordinate(lng, 34.39))).build();
    }

    private List<Long> find(List<Restaurant> near300, List<Restaurant> near600) {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(300.0))).thenReturn(near300);
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(600.0))).thenReturn(near600);
        return finder.findCafe(34.39, 132.46, null, Set.of(), LocalDate.of(2026, 12, 8), List.of(300, 600), 2500, 0.5, 3)
                .stream().map(c -> c.restaurant().getId()).toList();
    }

    @Test
    void 평점_있는_카페가_없으면_가까운_체인_카페_도넛_콜라보_카페는_빼고() {
        assertThat(find(List.of(chain(1, DONUT, 132.461), chain(2, STARBUCKS, 132.462), chain(3, ANIME, 132.460)), List.of()))
                .containsExactly(2L);
    }

    @Test
    void 같은_반경에_평점_있는_카페가_있으면_그쪽만() {
        assertThat(find(List.of(chain(2, STARBUCKS, 132.4601), rated(9, 132.462)), List.of())).containsExactly(9L);
    }

    @Test
    void 가까운_체인_카페가_먼_평점_카페보다_먼저() {
        assertThat(find(List.of(chain(2, STARBUCKS, 132.461)), List.of(chain(2, STARBUCKS, 132.461), rated(9, 132.465))))
                .containsExactly(2L);
    }

    @Test
    void 체인_카페끼리는_가까운_순() {
        Restaurant far = chain(4, STARBUCKS, 132.4625);
        Restaurant near = chain(5, STARBUCKS, 132.4605);
        assertThat(find(List.of(far, near), List.of())).containsExactly(5L, 4L);
    }
}
