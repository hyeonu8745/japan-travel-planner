package com.example.japantravelplanner.itinerary.reason;

import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.trip.entity.Interest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.example.japantravelplanner.itinerary.reason.ReasonFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 일정 항목을 고른 이유 (서버가 고른 기준에서만, 최대 3개) */
class StopReasonsTest {

    private static final ReasonContext NIGHT_AND_RAMEN =
            context(List.of(Interest.NIGHT_VIEW, Interest.TRADITIONAL), Set.of("ラーメン"), Set.of(), false);

    @Test
    void 관심사와_맞는_관광지는_관심사_이유가_먼저_붙는다() {
        var tower = attraction("東京タワー", "observation_deck,tourist_attraction", 4.6, 80_000);

        List<StopReason> reasons = StopReasons.of(attractionStop(2, tower, LegMode.TRANSIT, 20), NIGHT_AND_RAMEN);

        assertThat(reasons).extracting(StopReason::code)
                .containsExactly(ReasonCode.INTEREST, ReasonCode.HIGH_RATING, ReasonCode.MANY_REVIEWS);
        assertThat(reasons).extracting(StopReason::label)
                .containsExactly("관심사: 야경", "Google 평점 4.6", "리뷰 8만개");
    }

    @Test
    void 이유는_최대_세_개이고_중요한_것부터_남긴다() {
        // 관심사 둘(야경 = 대표 야경 명소, 전통 = 절) + 평점 + 리뷰 + 가까움 = 다섯 개
        var temple = attraction("浅草寺", "buddhist_temple", 4.6, 20_000);

        List<StopReason> reasons = StopReasons.of(attractionStop(2, temple, LegMode.WALK, 5), NIGHT_AND_RAMEN);

        assertThat(reasons).extracting(StopReason::label)
                .containsExactly("관심사: 야경", "관심사: 전통 명소", "Google 평점 4.6");
    }

    @Test
    void 야경_관심사는_대표_야경_명소에만_붙는다() {
        // 10-10 여행 8 v9: 무료 전망 공간 SKY VIEW와 낮의 레인보우브리지에 "관심사: 야경"이 붙었다
        var skyView = attraction("SKY VIEW", "observation_deck", 4.4, 513);
        var bridge = attraction("レインボーブリッジ", "bridge", 4.5, 4_059);
        var seaside = attraction("お台場海浜公園", "park", 4.4, 15_977);

        assertThat(StopReasons.of(attractionStop(2, skyView, LegMode.TRANSIT, 20), NIGHT_AND_RAMEN)).isEmpty();
        assertThat(StopReasons.of(attractionStop(2, bridge, LegMode.TRANSIT, 20), NIGHT_AND_RAMEN)).isEmpty();
        assertThat(StopReasons.of(attractionStop(5, seaside, LegMode.TRANSIT, 20), NIGHT_AND_RAMEN))
                .extracting(StopReason::label).containsExactly("관심사: 야경", "리뷰 1.5만개");
    }

    @Test
    void 평점이_기준보다_낮고_리뷰가_적으면_평점_이유는_없다() {
        var park = attraction("公園", "park", 4.5, 9_999);   // 4.5는 도쿄에서 흔해서 이유로 쓰지 않는다

        assertThat(StopReasons.of(attractionStop(2, park, LegMode.TRANSIT, 20), NIGHT_AND_RAMEN)).isEmpty();
    }

    @Test
    void 좋아하는_음식과_타베로그_평점이_식당의_이유가_된다() {
        var ramen = restaurant("麺屋", "ラーメン,つけ麺", 3.72);

        assertThat(StopReasons.of(mealStop(3, StopType.LUNCH, ramen, false, false), NIGHT_AND_RAMEN))
                .extracting(StopReason::label).containsExactly("좋아하는 음식", "타베로그 3.72");
    }

    @Test
    void 좋아하는_음식이_아니고_평점이_낮으면_식당_이유는_없다() {
        var sushi = restaurant("寿司屋", "寿司", 3.45);

        assertThat(StopReasons.of(mealStop(3, StopType.DINNER, sushi, false, false), NIGHT_AND_RAMEN)).isEmpty();
    }

    @Test
    void 걸어서_가까우면_숙소나_앞_장소에서_몇_분인지_붙인다() {
        var park = attraction("公園", "park", 4.0, 500);

        assertThat(StopReasons.of(attractionStop(1, park, LegMode.WALK, 6), ReasonContext.NONE))
                .extracting(StopReason::label).containsExactly("숙소에서 걸어서 6분");
        assertThat(StopReasons.of(attractionStop(3, park, LegMode.WALK, 0), ReasonContext.NONE))
                .extracting(StopReason::label).containsExactly("앞 장소에서 걸어서 1분");
        assertThat(StopReasons.of(attractionStop(3, park, LegMode.WALK, 11), ReasonContext.NONE)).isEmpty();
        assertThat(StopReasons.of(attractionStop(3, park, LegMode.TRANSIT, 5), ReasonContext.NONE)).isEmpty();
    }

    @Test
    void 여행_조건을_모르면_관심사와_좋아하는_음식_이유_없이_계산한다() {
        var tower = attraction("東京タワー", "observation_deck", 4.6, 500);
        var ramen = restaurant("麺屋", "ラーメン", 3.5);

        assertThat(StopReasons.of(attractionStop(2, tower, LegMode.TRANSIT, 20), null))
                .extracting(StopReason::code).containsExactly(ReasonCode.HIGH_RATING);
        assertThat(StopReasons.of(mealStop(3, StopType.LUNCH, ramen, false, false), null)).isEmpty();
    }

    @Test
    void 리뷰_수는_만_단위로_줄여_쓴다() {
        assertThat(StopReasons.tenThousands(10_000)).isEqualTo("1만");
        assertThat(StopReasons.tenThousands(12_345)).isEqualTo("1.2만");
        assertThat(StopReasons.tenThousands(19_999)).isEqualTo("1.9만");   // 반올림하지 않는다 (부풀리지 않게)
        assertThat(StopReasons.tenThousands(80_000)).isEqualTo("8만");
    }
}
