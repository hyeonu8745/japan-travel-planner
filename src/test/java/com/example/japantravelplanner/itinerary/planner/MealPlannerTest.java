package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MealPlannerTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final TravelPoint HOTEL = new TravelPoint("숙소", 35.70, 139.70);
    private static final TravelPoint ANCHOR = new TravelPoint("기준", 35.71, 139.79);

    // ── 장르 연속 방지 ──

    private static RestaurantCandidate restaurant(long id, String name, String genre, double score) {
        Restaurant r = Restaurant.builder().id(id).name(name).genre(genre).ratingVal(3.6).build();
        return new RestaurantCandidate(r, 300, score, false);
    }

    private final RestaurantCandidate ramen1 = restaurant(1, "与ろゐ屋", "ラーメン", 3.9);
    private final RestaurantCandidate ramen2 = restaurant(2, "라멘집2", "ラーメン,つけ麺", 3.8);
    private final RestaurantCandidate yakitori = restaurant(3, "야키토리집", "焼き鳥", 3.7);
    private final RestaurantCandidate soba = restaurant(4, "소바집", "そば", 3.6);

    @Test
    void 앞_끼니가_없으면_1위() {
        PlannedMeal m = MealPlanner.chooseAvoidingRepeat(Meal.LUNCH, ANCHOR,
                List.of(ramen1, ramen2, yakitori), Set.of());
        assertThat(m.chosen()).isEqualTo(ramen1);
        assertThat(m.genreRepeated()).isFalse();
        assertThat(m.alternatives()).containsExactly(ramen2, yakitori);
    }

    @Test
    void 앞_끼니와_장르가_겹치면_다음_후보() {
        // 전 끼니가 라멘 → 라멘집 두 곳을 건너뛰고 야키토리
        PlannedMeal m = MealPlanner.chooseAvoidingRepeat(Meal.DINNER, ANCHOR,
                List.of(ramen1, ramen2, yakitori, soba), Set.of("ラーメン"));
        assertThat(m.chosen()).isEqualTo(yakitori);
        assertThat(m.genreRepeated()).isFalse();
        assertThat(m.alternatives()).containsExactly(ramen1, ramen2);
    }

    @Test
    void 모두_겹치면_1위를_고르고_표시한다() {
        PlannedMeal m = MealPlanner.chooseAvoidingRepeat(Meal.DINNER, ANCHOR,
                List.of(ramen1, ramen2), Set.of("ラーメン"));
        assertThat(m.chosen()).isEqualTo(ramen1);
        assertThat(m.genreRepeated()).isTrue();
    }

    @Test
    void 기록은_고른_식당과_마지막_장르를_남긴다() {
        MealHistory history = new MealHistory();
        history.record(ramen2);
        assertThat(history.usedIds()).containsExactly(2L);
        assertThat(history.lastGenres()).containsExactlyInAnyOrder("ラーメン", "つけ麺");

        history.record(yakitori);
        assertThat(history.usedIds()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(history.lastGenres()).containsExactly("焼き鳥");
    }

    // ── 기준 위치 ──

    private static ScoredAttraction spot(long id, double lat, double lng, String types) {
        Attraction a = Attraction.builder().id(id).name("관광지" + id).types(types)
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
        return new ScoredAttraction(a, TripZone.ASAKUSA_UENO, 4.5);
    }

    private static DayRoute route(List<ScoredAttraction> morning, List<ScoredAttraction> afternoon,
                                  ScoredAttraction night) {
        DayZone day = new DayZone(1, LocalDate.of(2026, 10, 20), TripZone.ASAKUSA_UENO, List.of());
        return new DayRoute(day, morning, afternoon, night, List.of(), List.of(), null, 0);
    }

    private final ScoredAttraction a = spot(1, 35.700, 139.780, "museum");
    private final ScoredAttraction b = spot(2, 35.710, 139.790, "park");
    private final ScoredAttraction c = spot(3, 35.720, 139.800, "shinto_shrine");
    private final ScoredAttraction deck = spot(4, 35.730, 139.810, "observation_deck");

    @Test
    void 점심은_오전_마지막과_오후_첫_관광지의_중간() {
        TravelPoint p = MealPlanner.lunchAnchor(route(List.of(a), List.of(b, c), null), HOTEL);
        assertThat(p.lat()).isCloseTo(35.705, within(1e-9));
        assertThat(p.lng()).isCloseTo(139.785, within(1e-9));
    }

    @Test
    void 오후가_없으면_오전_마지막_관광지_근처에서_점심() {
        TravelPoint p = MealPlanner.lunchAnchor(route(List.of(a, b), List.of(), null), HOTEL);
        assertThat(p.lat()).isEqualTo(b.lat());
    }

    @Test
    void 저녁은_오후_마지막_관광지_근처() {
        TravelPoint p = MealPlanner.dinnerAnchor(route(List.of(a), List.of(b, c), null), HOTEL);
        assertThat(p.lat()).isEqualTo(c.lat());
    }

    @Test
    void 야경_장소가_있으면_저녁은_그_사이_중간() {
        TravelPoint p = MealPlanner.dinnerAnchor(route(List.of(a), List.of(b, c), deck), HOTEL);
        assertThat(p.lat()).isCloseTo(35.725, within(1e-9));
    }

    @Test
    void 관광지가_없으면_숙소_근처() {
        assertThat(MealPlanner.lunchAnchor(route(List.of(), List.of(), null), HOTEL)).isEqualTo(HOTEL);
        assertThat(MealPlanner.dinnerAnchor(route(List.of(), List.of(), null), HOTEL)).isEqualTo(HOTEL);
    }

    // ── 같은 묶음 감점 ──

    @Test
    void 라멘이_이미_나왔으면_다른_음식이_위로_올라온다() {
        MealHistory history = new MealHistory();
        history.record(ramen1);   // 점수 3.9 라멘이 이미 나옴

        // ramen2 3.8-0.15=3.65, yakitori 3.7, soba 3.6 → yakitori가 라멘2보다 위
        List<RestaurantCandidate> result = MealPlanner.rerank(List.of(ramen2, yakitori, soba), history, 0.15);
        assertThat(result).containsExactly(yakitori, ramen2, soba);
    }

    @Test
    void 라멘이_두_번_나왔으면_감점도_두_배() {
        MealHistory history = new MealHistory();
        history.record(ramen1);
        history.record(restaurant(9, "라멘집9", "つけ麺", 3.5));

        // ramen2 3.8-0.30=3.50, soba 3.6 → soba가 위
        List<RestaurantCandidate> result = MealPlanner.rerank(List.of(ramen2, soba), history, 0.15);
        assertThat(result).containsExactly(soba, ramen2);
    }

    @Test
    void 아직_안_나온_묶음은_순위_그대로() {
        List<RestaurantCandidate> result = MealPlanner.rerank(List.of(ramen1, ramen2, yakitori), new MealHistory(), 0.15);
        assertThat(result).containsExactly(ramen1, ramen2, yakitori);
    }

    @Test
    void 묶음이_없는_음식은_여러_번_나와도_감점이_없다() {
        MealHistory history = new MealHistory();
        history.record(yakitori);
        history.record(restaurant(8, "야키토리8", "焼き鳥", 3.5));

        List<RestaurantCandidate> result = MealPlanner.rerank(List.of(yakitori, soba), history, 0.15);
        assertThat(result).containsExactly(yakitori, soba);
    }
}