package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.zone.ZoneAssigner;
import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 관심사 체크박스에 없는 대화 속 취향 (10-10: "피규어 샵"이 반영되지 않았다) */
class WishTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private static Attraction attraction(long id, String name, String types, double lat, double lng) {
        return Attraction.builder().id(id).name(name).types(types).rating(4.3).reviewCnt(2_000)
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
    }

    @Test
    void 대화의_피규어_숍_이야기는_HOBBY로_읽는다() {
        List<PreferenceConstraint> constraints = List.of(
                new PreferenceConstraint(ConstraintCategory.ETC, "피규어 샵에 가고 싶음"),
                new PreferenceConstraint(ConstraintCategory.FOOD_PREFER, "라멘"));

        assertThat(Wish.from(constraints)).containsExactly(Wish.HOBBY);
    }

    @Test
    void 음식_조건이나_빈_목록에서는_취향을_찾지_않는다() {
        assertThat(Wish.from(List.of(new PreferenceConstraint(ConstraintCategory.FOOD_PREFER, "캐릭터 도시락")))).isEmpty();
        assertThat(Wish.from(null)).isEmpty();
    }

    @Test
    void 피규어_취향이면_하비_숍이_관심사만큼_가산점을_받는다() {
        CandidateProperties props = new CandidateProperties();
        AttractionScorer scorer = new AttractionScorer(props, new ZoneAssigner());
        Attraction kotobukiya = attraction(1, "コトブキヤ秋葉原館", "toy_store,store", 35.6990, 139.7720);
        Attraction park = attraction(2, "日比谷公園", "park", 35.6740, 139.7560);

        double without = scorer.score(List.of(kotobukiya, park), List.<Interest>of()).stream()
                .filter(s -> s.attraction() == kotobukiya).findFirst().orElseThrow().score();
        double with = scorer.score(List.of(kotobukiya, park), List.<Interest>of(), Set.of(Wish.HOBBY)).stream()
                .filter(s -> s.attraction() == kotobukiya).findFirst().orElseThrow().score();

        assertThat(with - without).isCloseTo(props.getInterestBonus(), org.assertj.core.data.Offset.offset(1e-9));
    }
}
