package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 11-08 리팩터링: 수정 서비스 3곳에 복사돼 있던 하루 복사·"같은 음식" 판단을 모은 DayCopies */
class DayCopiesTest {

    private final Attraction park = attraction(11, "공원", "park");
    private final Restaurant ramen1 = restaurant(21, "라멘1", "ラーメン");
    private final Restaurant sushi = restaurant(22, "스시", "寿司");
    private final Restaurant ramen2 = restaurant(23, "라멘2", "ラーメン");

    /** DAY1: 공원 → 점심 라멘1 → 저녁 스시 / DAY2: 저녁 라멘2 */
    private Itinerary itinerary() {
        ScheduledDay day1 = day(1, List.of(
                attractionStop(park, "10:00", "11:00", 60),
                mealStop(StopType.LUNCH, ramen1, false, false, "12:00", "13:00", 0),
                mealStop(StopType.DINNER, sushi, true, false, "18:00", "19:30", 5)), List.of(), List.of("안내"));
        ScheduledDay day2 = day(2, List.of(
                mealStop(StopType.DINNER, ramen2, false, false, "18:00", "19:30", 0)), List.of(), List.of());
        return ItineraryMapper.toEntity(trip(), 1, 9.0, List.of(day1, day2),
                List.of(window("09:30", "22:00"), window("09:30", "22:00")), Map.of(1, "설명1"),
                id -> park, id -> Map.of(21L, ramen1, 22L, sushi, 23L, ramen2).get(id));
    }

    @Test
    void 바뀌지_않은_날은_시각_설명_안내까지_그대로_복사하고_같은_음식_표시만_바꾼다() {
        ItineraryDay day = itinerary().getDays().get(0);
        ItineraryStop dinner = day.getStops().get(2);

        ItineraryDay copy = DayCopies.copyDay(day, Map.of(dinner, true));

        assertThat(copy.getDescription()).isEqualTo("설명1");
        assertThat(copy.getWarnings()).containsExactly("안내");
        assertThat(copy.getStops()).hasSize(3);
        ItineraryStop copied = copy.getStops().get(2);
        assertThat(copied).isNotSameAs(dinner);
        assertThat(copied.getRestaurant()).isSameAs(sushi);
        assertThat(copied.getArrivalTime()).isEqualTo(dinner.getArrivalTime());
        assertThat(copied.getWaitMinutes()).isEqualTo(5);
        assertThat(copied.isBudgetRelaxed()).isTrue();
        assertThat(copied.isGenreRepeated()).isTrue();                          // 바꾼 값
        assertThat(copy.getStops().get(1).isGenreRepeated()).isFalse();        // 없으면 원래 값
    }

    @Test
    void 같은_음식_표시는_날짜를_넘어_앞_끼니와_비교한다() {
        Itinerary itinerary = itinerary();
        ItineraryStop dinner1 = itinerary.getDays().get(0).getStops().get(2);
        ItineraryStop dinner2 = itinerary.getDays().get(1).getStops().get(0);

        // 지금: 라멘1 → 스시 → 라멘2 : 겹치는 곳 없음
        Map<Object, Boolean> now = DayCopies.genreRepeatedFlags(itinerary, null, List.of(),
                key -> ((ItineraryStop) key).getRestaurant());
        assertThat(now.get(dinner2)).isFalse();

        // DAY1 저녁을 라멘으로 바꾸면: 라멘1 → 라멘(바꾼 곳, 겹침) → 라멘2(다음 날도 겹침)
        Map<Object, Boolean> replaced = DayCopies.genreRepeatedFlags(itinerary, null, List.of(),
                key -> key == dinner1 ? ramen2 : ((ItineraryStop) key).getRestaurant());
        assertThat(replaced.get(dinner1)).isTrue();
        assertThat(replaced.get(dinner2)).isTrue();
    }

    @Test
    void 바뀐_날은_새_순서의_열쇠로_판단한다() {
        Itinerary itinerary = itinerary();
        ItineraryDay day1 = itinerary.getDays().get(0);
        // DAY1 에서 스시 저녁을 지운 순서: 라멘1 → (다음 날) 라멘2 가 겹친다
        List<ItineraryStop> edited = List.of(day1.getStops().get(0), day1.getStops().get(1));
        Map<Object, Boolean> flags = DayCopies.genreRepeatedFlags(itinerary, day1, edited,
                key -> ((ItineraryStop) key).getRestaurant());

        assertThat(flags).doesNotContainKey(day1.getStops().get(2));
        assertThat(flags.get(itinerary.getDays().get(1).getStops().get(0))).isTrue();
    }

    @Test
    void 시각을_다시_계산하면_새로_생기는_안내만_시간_안내다() {
        assertThat(DayCopies.isTimeWarning("점심 시간이 13:30을 넘겼어요.")).isTrue();
        assertThat(DayCopies.isTimeWarning("저녁 시간이 20:00을 넘겼어요.")).isTrue();
        assertThat(DayCopies.isTimeWarning("일정이 21:00을 넘겨 끝나요.")).isTrue();
        assertThat(DayCopies.isTimeWarning("라멘1은(는) 이 날이 정기 휴일이에요.")).isFalse();
    }
}
