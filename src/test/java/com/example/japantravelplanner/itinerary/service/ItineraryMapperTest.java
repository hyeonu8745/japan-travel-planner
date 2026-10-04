package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DropReason;
import com.example.japantravelplanner.itinerary.planner.DroppedStop;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

class ItineraryMapperTest {

    private final Trip trip = trip();
    private final Attraction cat = attraction(11, "新宿東口の猫", "tourist_attraction");
    private final Attraction deck = attraction(12, "東京都庁 北展望室", "observation_deck");
    private final Restaurant ramen = restaurant(21, "つけ麺 五ノ神製作所", "つけ麺");

    private final List<Long> attractionLookups = new ArrayList<>();
    private final List<Long> restaurantLookups = new ArrayList<>();

    private final ScheduledDay day1 = day(1, List.of(
                    attractionStop(cat, "16:15", "16:55", 40),
                    mealStop(StopType.DINNER, ramen, true, true, "18:00", "19:30", 24),
                    attractionStop(deck, "19:46", "20:46", 60)),
            List.of(new DroppedStop("ゴジラヘッド", DropReason.TIME_SHORTAGE)),
            List.of("저녁 식당 후보를 찾지 못했어요."));
    private final ScheduledDay day2 = day(2, List.of(), List.of(), List.of("이 날은 일정을 넣을 수 없어요."));

    private Itinerary map(Map<Integer, String> descriptions) {
        return ItineraryMapper.toEntity(trip, 3, 9.0, List.of(day1, day2),
                List.of(window("16:00", "22:00"), NO_WINDOW), descriptions,
                id -> {
                    attractionLookups.add(id);
                    return id == 11L ? cat : deck;
                },
                id -> {
                    restaurantLookups.add(id);
                    return ramen;
                });
    }

    @Test
    void 일정_머리글은_버전과_환율을_담는다() {
        Itinerary itinerary = map(Map.of());

        assertThat(itinerary.getTrip()).isSameAs(trip);
        assertThat(itinerary.getVersionNo()).isEqualTo(3);
        assertThat(itinerary.getKrwPerYen()).isEqualTo(9.0);
        assertThat(itinerary.getDays()).hasSize(2);
    }

    @Test
    void 하루는_날짜_지역_시간대_설명을_담는다() {
        Itinerary itinerary = map(Map.of(1, "신주쿠의 하루예요."));
        ItineraryDay first = itinerary.getDays().get(0);
        ItineraryDay second = itinerary.getDays().get(1);

        assertThat(first.getDayNumber()).isEqualTo(1);
        assertThat(first.getDate()).isEqualTo(day1.date());
        assertThat(first.getZone()).isEqualTo(TripZone.SHINJUKU);
        assertThat(first.getWindowStart()).isEqualTo(LocalTime.of(16, 0));
        assertThat(first.getWindowEnd()).isEqualTo(LocalTime.of(22, 0));
        assertThat(first.getDescription()).isEqualTo("신주쿠의 하루예요.");

        assertThat(second.getDescription()).isNull();   // 설명이 없는 날은 null
    }

    @Test
    void 일정을_넣을_수_없는_날은_시간대가_비어_있다() {
        ItineraryDay empty = map(Map.of()).getDays().get(1);

        assertThat(empty.getWindowStart()).isNull();
        assertThat(empty.getWindowEnd()).isNull();
        assertThat(empty.getStops()).isEmpty();
        assertThat(empty.getWarnings()).containsExactly("이 날은 일정을 넣을 수 없어요.");
    }

    @Test
    void 항목은_시간_순서대로_1부터_번호가_붙는다() {
        List<ItineraryStop> stops = map(Map.of()).getDays().get(0).getStops();

        assertThat(stops).extracting(ItineraryStop::getStopOrder).containsExactly(1, 2, 3);
        assertThat(stops).extracting(ItineraryStop::getType)
                .containsExactly(StopType.ATTRACTION, StopType.DINNER, StopType.ATTRACTION);
    }

    @Test
    void 관광지_항목은_관광지만_식당_항목은_식당만_참조한다() {
        List<ItineraryStop> stops = map(Map.of()).getDays().get(0).getStops();

        assertThat(stops.get(0).getAttraction()).isSameAs(cat);
        assertThat(stops.get(0).getRestaurant()).isNull();

        assertThat(stops.get(1).getRestaurant()).isSameAs(ramen);
        assertThat(stops.get(1).getAttraction()).isNull();

        assertThat(stops.get(2).getAttraction()).isSameAs(deck);
    }

    @Test
    void 장소는_id로_참조만_가져오고_종류에_맞는_쪽만_조회한다() {
        map(Map.of());

        assertThat(attractionLookups).containsExactly(11L, 12L);
        assertThat(restaurantLookups).containsExactly(21L);
    }

    @Test
    void 시각_체류_대기_이동이_그대로_복사된다() {
        ItineraryStop dinner = map(Map.of()).getDays().get(0).getStops().get(1);

        assertThat(dinner.getArrivalTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(dinner.getDepartureTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(dinner.getStayMinutes()).isEqualTo(90);
        assertThat(dinner.getWaitMinutes()).isEqualTo(24);
        assertThat(dinner.getTravelMode()).isEqualTo(LegMode.WALK);
        assertThat(dinner.getTravelMinutes()).isEqualTo(10);
        assertThat(dinner.getTravelDistanceMeters()).isEqualTo(763);
        assertThat(dinner.isTravelEstimated()).isFalse();
    }

    @Test
    void 식당의_예산_상향과_장르_중복_표시가_저장된다() {
        List<ItineraryStop> stops = map(Map.of()).getDays().get(0).getStops();

        assertThat(stops.get(1).isBudgetRelaxed()).isTrue();
        assertThat(stops.get(1).isGenreRepeated()).isTrue();
        // 관광지는 해당 없음
        assertThat(stops.get(0).isBudgetRelaxed()).isFalse();
        assertThat(stops.get(0).isGenreRepeated()).isFalse();
    }

    @Test
    void 빠진_장소와_안내_문장이_저장된다() {
        ItineraryDay first = map(Map.of()).getDays().get(0);

        assertThat(first.getDropped()).hasSize(1);
        assertThat(first.getDropped().get(0).getName()).isEqualTo("ゴジラヘッド");
        assertThat(first.getDropped().get(0).getReason()).isEqualTo(DropReason.TIME_SHORTAGE);
        assertThat(first.getWarnings()).containsExactly("저녁 식당 후보를 찾지 못했어요.");
    }
}