package com.example.japantravelplanner.itinerary.budget;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetSummaryServiceTest {

    private static Restaurant restaurant(long id, Integer lunchMin, Integer lunchMax, Integer dinnerMin, Integer dinnerMax) {
        return Restaurant.builder().id(id).name("店" + id).nameKo("가게" + id)
                .budgetLunch("￥" + lunchMin).budgetDinner("￥" + dinnerMin)
                .budgetLunchMin(lunchMin).budgetLunchMax(lunchMax)
                .budgetDinnerMin(dinnerMin).budgetDinnerMax(dinnerMax).build();
    }

    private static ItineraryStop stop(int order, StopType type, Restaurant r) {
        ItineraryStop.ItineraryStopBuilder b = ItineraryStop.builder().stopOrder(order).type(type)
                .arrivalTime(LocalTime.of(10, 0)).departureTime(LocalTime.of(11, 0)).stayMinutes(60)
                .travelMode(LegMode.TRANSIT).travelMinutes(10).travelDistanceMeters(800);
        return type == StopType.ATTRACTION
                ? b.attraction(Attraction.builder().id(99L).name("寺").build()).build()
                : b.restaurant(r).build();
    }

    private static Itinerary itinerary(Trip trip, List<List<ItineraryStop>> days) {
        Itinerary itinerary = Itinerary.create(trip, 2, 9.0);
        for (int i = 0; i < days.size(); i++) {
            ItineraryDay day = ItineraryDay.create(i + 1, LocalDate.of(2026, 11, 1 + i), null,
                    LocalTime.of(9, 0), LocalTime.of(21, 0), "", List.of(), List.of());
            days.get(i).forEach(day::addStop);
            itinerary.addDay(day);
        }
        return itinerary;
    }

    @Test
    void 끼니별_예산을_더하고_인원을_곱해_원화로_바꾼다() {
        Trip trip = Trip.builder().id(1L).travelers(2).budget(1_000_000).build();
        Itinerary itinerary = itinerary(trip, List.of(
                List.of(stop(1, StopType.ATTRACTION, null),
                        stop(2, StopType.LUNCH, restaurant(1, 1000, 1999, 5000, 5999)),
                        stop(3, StopType.DINNER, restaurant(2, null, null, 3000, 3999))),
                List.of(stop(1, StopType.LUNCH, restaurant(3, null, 999, null, null)))));

        BudgetSummaryDto dto = BudgetSummaryService.summarize(trip, itinerary, 0.4);

        // 1인: 점심 1000~1999 + 저녁 3000~3999 + 점심 999~999
        assertThat(dto.perPerson().minYen()).isEqualTo(4999);
        assertThat(dto.perPerson().maxYen()).isEqualTo(6997);
        assertThat(dto.food().minYen()).isEqualTo(9998);
        assertThat(dto.food().maxKrw()).isEqualTo(Math.round(13994 * 9.0));
        assertThat(dto.food().meals()).isEqualTo(3);
        assertThat(dto.food().unknownMeals()).isZero();
        assertThat(dto.days()).hasSize(2);
        assertThat(dto.days().get(0).meals()).hasSize(2);
        assertThat(dto.days().get(0).minYen()).isEqualTo(8000);
        assertThat(dto.days().get(1).meals().get(0).budgetText()).isEqualTo("￥null");
        assertThat(dto.foodBudgetKrw()).isEqualTo(400_000);
        assertThat(dto.verdict()).isEqualTo("WITHIN");
        assertThat(dto.note()).contains("교통비").contains("아침");
        assertThat(dto.exchangeRateSourceLabel()).isEqualTo("고정 환율");
    }

    @Test
    void 식비_예산과_비교해_판정한다() {
        Restaurant r = restaurant(1, 10000, 20000, 10000, 20000);
        // 1인 1끼 10000~20000엔 × 9원 = 90,000~180,000원
        assertThat(verdict(80_000, r)).isEqualTo("OVER");
        assertThat(verdict(100_000, r)).isEqualTo("MAYBE_OVER");
        assertThat(verdict(200_000, r)).isEqualTo("WITHIN");
    }

    private String verdict(int foodBudget, Restaurant r) {
        Trip trip = Trip.builder().id(1L).travelers(1).budget(foodBudget).build();
        Itinerary itinerary = itinerary(trip, List.of(List.of(stop(1, StopType.LUNCH, r))));
        return BudgetSummaryService.summarize(trip, itinerary, 1.0).verdict();
    }

    @Test
    void 예산을_모르는_끼니만_있으면_UNKNOWN() {
        Trip trip = Trip.builder().id(1L).travelers(null).budget(null).build();
        Itinerary itinerary = itinerary(trip, List.of(List.of(stop(1, StopType.LUNCH, restaurant(1, null, null, 3000, 3999)))));

        BudgetSummaryDto dto = BudgetSummaryService.summarize(trip, itinerary, 0.4);

        assertThat(dto.travelers()).isEqualTo(1);
        assertThat(dto.food().unknownMeals()).isEqualTo(1);
        assertThat(dto.verdict()).isEqualTo("UNKNOWN");
        assertThat(dto.tripBudgetShare()).isZero();
        assertThat(dto.days().get(0).meals().get(0).minYen()).isNull();
    }

    @Test
    void 범위_한쪽만_있으면_그_값을_쓴다() {
        assertThat(BudgetSummaryService.range(null, 999)).containsExactly(999, 999);
        assertThat(BudgetSummaryService.range(0, 999)).containsExactly(999, 999);   // "～￥999"가 0으로 저장된 경우
        assertThat(BudgetSummaryService.range(0, 0)).isNull();
        assertThat(BudgetSummaryService.range(3000, null)).containsExactly(3000, 3000);
        assertThat(BudgetSummaryService.range(4000, 3000)).containsExactly(3000, 4000);
        assertThat(BudgetSummaryService.range(null, null)).isNull();
        assertThat(BudgetSummaryService.sourceLabel("KOREAEXIM")).isEqualTo("한국수출입은행");
    }
}
