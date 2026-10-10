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
        Itinerary itinerary = Itinerary.create(Trip.builder().id(1L).build(), 3, 9.5);
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
                .endsWith("장소마다 고른 이유도 함께 표시해 두었어요.");
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

        assertThat(summary).isEqualTo("일정을 이렇게 짰어요.\n장소마다 고른 이유도 함께 표시해 두었어요.");
    }
}
