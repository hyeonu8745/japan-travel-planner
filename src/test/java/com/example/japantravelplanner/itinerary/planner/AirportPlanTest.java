package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 공항 이동 (10-15): 비행기 시각에서 첫날 시작·마지막 날 끝 시각과 안내 단계를 계산한다 */
class AirportPlanTest {

    private static Trip trip(String arrivalAirport, LocalTime arrival, String departureAirport, LocalTime departure) {
        return Trip.builder().id(1L).startDate(LocalDate.of(2026, 10, 25)).endDate(LocalDate.of(2026, 10, 27))
                .arrivalAirport(arrivalAirport).arrivalFlightTime(arrival)
                .departureAirport(departureAirport).departureFlightTime(departure).build();
    }

    private static List<String> times(List<AirportPlan.Step> steps) {
        return steps.stream().map(s -> s.time() + " " + s.kind() + " " + s.position()).toList();
    }

    @Test
    void 하네다_오전_도착이면_입국_짐_찾기_이동_짐_맡기기_뒤에_시작하고_점심도_넣는다() {
        Trip t = trip("HND", LocalTime.of(10, 30), null, null);

        // 10:30 + 입국 60 + 하네다→숙소 45 + 짐 맡기기 20 = 12:35
        assertThat(AirportPlan.firstDayStart(t)).isEqualTo(12 * 60 + 35);
        assertThat(times(AirportPlan.arrivalSteps(t))).containsExactly(
                "10:30 FLIGHT_ARRIVAL BEFORE", "11:30 AIRPORT_TO_HOTEL BEFORE", "12:15 HOTEL_DROP BEFORE");
        assertThat(AirportPlan.arrivalSteps(t).get(2).label()).isEqualTo("숙소에 짐 맡기기 (체크인 전)");
        assertThat(AirportPlan.arrivalSteps(t).get(1).label()).contains("하네다 공항").contains("약 45분");

        DayWindow window = DayWindow.of(true, false, null, null, false, AirportPlan.firstDayStart(t), null);
        assertThat(window.start()).isEqualTo(LocalTime.of(12, 35));
        assertThat(window.lunch()).isTrue();
    }

    @Test
    void 나리타_오후_도착이면_체크인이고_점심은_빠진다() {
        Trip t = trip("NRT", LocalTime.of(13, 0), null, null);

        assertThat(AirportPlan.arrivalSteps(t).get(2).time()).isEqualTo(LocalTime.of(15, 20));   // 13:00 + 60 + 80
        assertThat(AirportPlan.arrivalSteps(t).get(2).label()).isEqualTo("숙소 체크인");
        DayWindow window = DayWindow.of(true, false, null, null, false, AirportPlan.firstDayStart(t), null);
        assertThat(window.start()).isEqualTo(LocalTime.of(15, 40));
        assertThat(window.lunch()).isFalse();
        assertThat(window.dinner()).isTrue();
    }

    @Test
    void 하네다_18시_출발이면_14시30분까지_관광하고_숙소에서_짐_찾아_공항으로() {
        Trip t = trip(null, null, "HND", LocalTime.of(18, 0));

        // 공항 도착 16:00 ← 숙소 출발 15:15 ← 짐 찾기 15:00 ← 마지막 장소 출발 14:30
        assertThat(AirportPlan.lastDayEnd(t)).isEqualTo(14 * 60 + 30);
        DayWindow window = DayWindow.of(false, true, null, null, false, null, AirportPlan.lastDayEnd(t));
        assertThat(window.end()).isEqualTo(LocalTime.of(14, 30));
        assertThat(window.lunch()).isTrue();
        assertThat(window.dinner()).isFalse();

        assertThat(times(AirportPlan.departureSteps(t, window.start()))).containsExactly(
                "09:15 CHECKOUT BEFORE", "15:00 HOTEL_PICKUP AFTER", "15:15 HOTEL_TO_AIRPORT AFTER",
                "18:00 FLIGHT_DEPARTURE AFTER");
    }

    @Test
    void 아침_비행기면_마지막_날은_관광_없이_체크아웃하고_바로_공항() {
        Trip t = trip(null, null, "NRT", LocalTime.of(9, 0));

        DayWindow window = DayWindow.of(false, true, null, null, false, null, AirportPlan.lastDayEnd(t));
        assertThat(window.isEmpty()).isTrue();
        // 숙소 출발 05:40 (09:00 - 120 - 80), 짐 찾기 05:25, 체크아웃은 그 전
        assertThat(times(AirportPlan.departureSteps(t, null))).containsExactly(
                "05:10 CHECKOUT BEFORE", "05:25 HOTEL_PICKUP AFTER", "05:40 HOTEL_TO_AIRPORT AFTER",
                "09:00 FLIGHT_DEPARTURE AFTER");
    }

    @Test
    void 밤늦게_도착하면_첫날은_비고_시각은_자정을_넘겨_보여준다() {
        Trip t = trip("NRT", LocalTime.of(23, 30), null, null);

        assertThat(DayWindow.of(true, false, null, null, true, AirportPlan.firstDayStart(t), null).isEmpty()).isTrue();
        assertThat(times(AirportPlan.arrivalSteps(t))).containsExactly(
                "23:30 FLIGHT_ARRIVAL BEFORE", "00:30 AIRPORT_TO_HOTEL BEFORE", "01:50 HOTEL_DROP BEFORE");
    }

    @Test
    void 비행기_시각이_없으면_예전처럼_대화의_숙소_도착_시각을_쓰고_있으면_비행기_시각이_먼저() {
        Trip none = trip(null, null, null, null);
        assertThat(AirportPlan.firstDayStart(none)).isNull();
        assertThat(AirportPlan.arrivalSteps(none)).isEmpty();
        assertThat(DayWindow.of(true, false, LocalTime.of(14, 0), null, false,
                AirportPlan.firstDayStart(none), null).start()).isEqualTo(LocalTime.of(15, 0));

        Trip withFlight = trip("HND", LocalTime.of(10, 30), null, null);
        assertThat(DayWindow.of(true, false, LocalTime.of(14, 0), null, false,
                AirportPlan.firstDayStart(withFlight), null).start()).isEqualTo(LocalTime.of(12, 35));

        // 시각만 있고 공항이 없거나 모르는 공항이면 계산하지 않는다
        assertThat(AirportPlan.firstDayStart(trip(null, LocalTime.of(10, 0), null, null))).isNull();
        assertThat(AirportPlan.lastDayEnd(trip(null, null, "KIX", LocalTime.of(18, 0)))).isNull();
    }
}
