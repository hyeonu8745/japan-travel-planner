package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.restaurant.entity.ChainBrand;
import com.example.japantravelplanner.restaurant.entity.ChainCategory;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.entity.RestaurantSource;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 체인점(평점 없음, 장르·예산은 브랜드)을 끼니 후보로 쓰기 (11-18 확장) */
class RestaurantFinderChainTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final FoodFilter NO_FILTER = new FoodFilter(Set.of(), Set.of(), false);

    private static final ChainBrand YOSHINOYA = brand("吉野家", ChainCategory.MEAL, "규동", 500, 1000);
    private static final ChainBrand ICHIRAN = brand("一蘭", ChainCategory.MEAL, "라멘", 1000, 1600);
    private static final ChainBrand STARBUCKS = brand("スターバックス", ChainCategory.CAFE, "카페", 500, 1000);

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantFinder finder = new RestaurantFinder(repository, new MealProperties());

    private static ChainBrand brand(String name, ChainCategory category, String genreKo, int min, int max) {
        return ChainBrand.builder().name(name).category(category).genreKo(genreKo).budgetMin(min).budgetMax(max).build();
    }

    private static Restaurant chain(long id, ChainBrand brand, double lngOffset) {
        return Restaurant.builder().id(id).name(brand.getName()).source(RestaurantSource.CHAIN).brand(brand)
                .location(GF.createPoint(new Coordinate(139.70 + lngOffset, 35.69))).build();
    }

    private static Restaurant tabelog(long id, String name, double rating, int lunchMax, double lngOffset) {
        return Restaurant.builder().id(id).name(name).genre("洋食").ratingVal(rating).reviewCnt(100)
                .budgetLunchMax(lunchMax).budgetDinnerMax(lunchMax * 2)
                .location(GF.createPoint(new Coordinate(139.70 + lngOffset, 35.69))).build();
    }

    private List<String> lunch(int maxYen, FoodFilter filter) {
        return finder.find(35.69, 139.70, Meal.LUNCH, maxYen, filter, Set.of(), 10).stream()
                .map(c -> c.restaurant().getName()).toList();
    }

    @Test
    void 평점_없는_체인점도_브랜드_예산으로_후보가_되고_타베로그_식당보다_뒤에_온다() {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(chain(1, YOSHINOYA, 0.0001), tabelog(2, "洋食屋", 3.3, 1500, 0.002)));

        assertThat(lunch(1500, NO_FILTER)).containsExactly("洋食屋", "吉野家");
    }

    @Test
    void 예산이_적으면_체인점만_남는다() {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(chain(1, YOSHINOYA, 0.0001), tabelog(2, "洋食屋", 3.8, 2000, 0.001)));

        assertThat(lunch(1000, NO_FILTER)).containsExactly("吉野家");
    }

    @Test
    void 카페_체인은_끼니_후보가_아니다() {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(chain(1, STARBUCKS, 0.0001), chain(2, YOSHINOYA, 0.0002)));

        assertThat(lunch(1500, NO_FILTER)).containsExactly("吉野家");
    }

    @Test
    void 피할_음식은_체인점에도_적용된다_브랜드_장르를_타베로그_장르로_바꿔서() {
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(chain(1, ICHIRAN, 0.0001), chain(2, YOSHINOYA, 0.0002)));
        FoodFilter noRamen = new FoodFilter(Set.of(), Set.of("ラーメン"), false);

        assertThat(lunch(2000, noRamen)).containsExactly("吉野家");
    }

    @Test
    void 체인점은_후보_수에_세지_않아서_가까운_체인점만으로_검색을_멈추지_않는다() {
        // 500m 안에는 체인점 3곳뿐 → 1km 까지 넓혀서 타베로그 식당을 본다
        List<Restaurant> near = List.of(chain(1, YOSHINOYA, 0.0001), chain(2, YOSHINOYA, 0.0002), chain(3, ICHIRAN, 0.0003));
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(500.0))).thenReturn(near);
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(1000.0))).thenReturn(List.of(
                near.get(0), near.get(1), near.get(2),
                tabelog(4, "A", 3.6, 1500, 0.008), tabelog(5, "B", 3.6, 1500, 0.009), tabelog(6, "C", 3.6, 1500, 0.0095)));

        assertThat(lunch(2000, NO_FILTER)).startsWith("A", "B", "C");
    }

    // ── 11-18: 식당 데이터가 거의 없는 곳, 서서 마시는 가게 ──

    @Test
    void 일점오킬로_안에_없으면_예산을_올려보고_그래도_없으면_삼킬로까지_넓힌다() {
        // 산 위 전망대 근처: 1.5km 안에는 없고 2km 에 있다
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(repository.findWithinRadius(anyDouble(), anyDouble(), eq(2000.0)))
                .thenReturn(List.of(tabelog(1, "山の下の食堂", 3.6, 1500, 0.018)));

        List<RestaurantCandidate> found = finder.find(35.69, 139.70, Meal.LUNCH, 2000, NO_FILTER, Set.of(), 10);

        assertThat(found).extracting(c -> c.restaurant().getName()).containsExactly("山の下の食堂");
        assertThat(found.get(0).budgetRelaxed()).isFalse();
    }

    @Test
    void 서서_마시는_가게와_완전_예약제는_끼니_후보가_아니다() {
        Restaurant standing = tabelog(1, "ビールスタンド", 3.9, 1500, 0.0001);
        standing.setStandingBar(true);
        Restaurant reserved = tabelog(2, "予約のみ", 4.2, 1500, 0.0002);
        reserved.setReservationOnly(true);
        when(repository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(standing, reserved, tabelog(3, "洋食屋", 3.5, 1500, 0.0003)));

        assertThat(lunch(2000, NO_FILTER)).containsExactly("洋食屋");
    }

    @Test
    void 立ち飲み가_붙은_맥줏집과_군고구마_가게는_저녁이_아니다() {
        assertThat(RestaurantFinder.isDrinkLed("ビアバー,立ち飲み,ビアホール")).isTrue();
        assertThat(RestaurantFinder.isSnackGenre("焼き芋・大学芋")).isTrue();
    }
}
