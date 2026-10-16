package com.example.japantravelplanner.itinerary.reason;

import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DropReason;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.example.japantravelplanner.itinerary.reason.ReasonFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 채팅에 남기는 "이렇게 짰어요" (저장된 일정과 여행 조건의 사실만) */
class PlanSummaryTest {

    private static Itinerary itinerary(List<ItineraryStop> stops, List<DroppedPlace> dropped) {
        return itinerary(stops, dropped, 3);
    }

    private static Itinerary itinerary(List<ItineraryStop> stops, List<DroppedPlace> dropped, int version) {
        Itinerary itinerary = Itinerary.create(Trip.builder().id(1L).build(), version, 9.5);
        ItineraryDay day = ItineraryDay.create(1, LocalDate.of(2026, 10, 20), TripZone.SHINJUKU,
                null, null, null, dropped, List.of());
        stops.forEach(day::addStop);
        itinerary.addDay(day);
        return itinerary;
    }

    private static List<ItineraryStop> stops() {
        return List.of(
                attractionStop(1, attraction("東京都庁第一本庁舎 北展望室", "observation_deck", 4.4, 30_000), LegMode.WALK, 8),
                attractionStop(2, attraction("SHIBUYA SKY", "observation_deck", 4.7, 20_000), LegMode.TRANSIT, 15),
                mealStop(3, StopType.LUNCH, restaurant("麺屋", "ラーメン", 3.7), false, false),
                attractionStop(4, attraction("東京タワー", "observation_deck,landmark", 4.6, 80_000), LegMode.TRANSIT, 20),
                mealStop(5, StopType.DINNER, restaurant("焼肉屋", "焼肉", 3.6), true, true));
    }

    @Test
    void 여행의_첫_일정에만_사용법과_주의사항을_붙이고_다시_만들면_한_줄만() {
        // 10-11 사용자 요청: 채팅에 사용법·주의사항 안내. 다시 만들 때마다 요약이 쌓여서 첫 일정에만 자세히
        ReasonContext ctx = context(List.of(), Set.of(), Set.of(), false);

        String first = PlanSummary.write(itinerary(stops(), List.of(), 1), ctx);
        String again = PlanSummary.write(itinerary(stops(), List.of(), 2), ctx);

        assertThat(first).endsWith("장소마다 고른 이유도 함께 표시해 두었어요.\n\n" + PlanSummary.GUIDE)
                .contains("[이렇게 써 보세요]").contains("[참고해 주세요]").contains("임시 휴업");
        assertThat(again).endsWith(PlanSummary.EDIT_HINT).doesNotContain("[참고해 주세요]");
    }

    @Test
    void 관심사별로_넣은_곳과_식당_조건_반영을_사실대로_쓴다() {
        ReasonContext ctx = context(List.of(Interest.NIGHT_VIEW), Set.of("ラーメン"), Set.of("寿司"), true);

        String summary = PlanSummary.write(itinerary(stops(), List.of()), ctx);

        assertThat(summary).startsWith("일정을 이렇게 짰어요.\n")
                .contains("- 관심사 '야경'에 맞는 곳: 東京都庁第一本庁舎 北展望室, SHIBUYA SKY 외 1곳\n")
                .contains("- 식당은 동선 가까이에서 평점이 높은 곳으로 골랐어요.\n")
                .contains("- 좋아하는 음식은 1끼에 넣었어요.\n")
                .contains("- 피하고 싶은 음식이 들어간 식당은 뺐어요.\n")
                .contains("- 담배 연기가 있을 수 있는 이자카야·바는 뺐어요.\n")
                .contains("- 예산 안에서 찾지 못해 예산을 조금 넘는 식당이 1곳 있어요.\n")
                .contains("- 앞 끼니와 같은 음식을 피하지 못한 식당이 1곳 있어요.\n")
                .endsWith("장소마다 고른 이유도 함께 표시해 두었어요.\n" + PlanSummary.EDIT_HINT);
    }

    @Test
    void 반영하지_못한_관심사와_좋아하는_음식도_숨기지_않는다() {
        ReasonContext ctx = context(List.of(Interest.SHOPPING), Set.of("寿司"), Set.of(), false);

        String summary = PlanSummary.write(itinerary(stops(), List.of()), ctx);

        assertThat(summary).contains("- 관심사 '쇼핑'에 맞는 곳은 이번 동선에 넣지 못했어요.\n")
                .contains("- 좋아하는 음식은 동선 근처에서 찾지 못해 넣지 못했어요.\n")
                .doesNotContain("피하고 싶은").doesNotContain("담배");
    }

    @Test
    void 빠진_장소가_있으면_몇_곳인지_알린다() {
        String summary = PlanSummary.write(itinerary(stops(),
                List.of(new DroppedPlace("六本木ヒルズ", DropReason.TIME_SHORTAGE))), ReasonContext.NONE);

        assertThat(summary).contains("일정에서 빠진 장소가 1곳 있어요.");
    }

    @Test
    void 조건이_없고_식당도_없으면_머리말과_맺음말만_남는다() {
        String summary = PlanSummary.write(itinerary(List.of(
                attractionStop(1, attraction("公園", "park", 4.0, 100), LegMode.TRANSIT, 20)), List.of()), null);

        assertThat(summary).isEqualTo("일정을 이렇게 짰어요.\n장소마다 고른 이유도 함께 표시해 두었어요.\n" + PlanSummary.EDIT_HINT);
    }

    @Test
    void 대화에서_말한_취향을_넣었는지도_알린다() {
        ReasonContext ctx = new ReasonContext(List.of(), null,
                Set.of(com.example.japantravelplanner.itinerary.candidate.Wish.HOBBY));
        List<ItineraryStop> withShop = List.of(
                attractionStop(1, attraction("コトブキヤ秋葉原館", "toy_store,store", 4.3, 2_494), LegMode.WALK, 8));

        assertThat(PlanSummary.write(itinerary(withShop, List.of()), ctx))
                .contains("- 말씀하신 '피규어·애니' 취향에 맞는 곳: コトブキヤ秋葉原館\n");
        assertThat(PlanSummary.write(itinerary(stops(), List.of()), ctx))
                .contains("- 말씀하신 '피규어·애니' 취향에 맞는 곳은 이번 동선에 넣지 못했어요.\n");
    }

    @Test
    void 알레르기는_식당_종류로만_걸렀다고_알리고_거를_수_없는_재료는_이름을_든다() {
        // 10-16: 땅콩·우유처럼 여러 요리에 들어가는 재료는 장르로 뺄 수 없다는 사실을 숨기지 않는다
        var withWords = new ReasonContext(List.of(), new com.example.japantravelplanner.itinerary.meal.FoodFilter(
                Set.of(), Set.of("そば"), false, true, List.of("땅콩", "우유")));
        var genreOnly = new ReasonContext(List.of(), new com.example.japantravelplanner.itinerary.meal.FoodFilter(
                Set.of(), Set.of("そば"), false, true, List.of()));

        assertThat(PlanSummary.write(itinerary(stops(), List.of()), withWords))
                .contains("- 땅콩·우유는 여러 요리에 들어가서 식당 종류로 거를 수 없어요. 주문할 때 꼭 확인해 주세요.\n");
        assertThat(PlanSummary.write(itinerary(stops(), List.of()), genreOnly))
                .contains("- 알레르기는 식당 종류로만 걸렀어요. 메뉴 원재료는 주문할 때 꼭 확인해 주세요.\n");
        assertThat(PlanSummary.write(itinerary(stops(), List.of()), context(List.of(), Set.of(), Set.of(), false)))
                .doesNotContain("알레르기");
    }
}
