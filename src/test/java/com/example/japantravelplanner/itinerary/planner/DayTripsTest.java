package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.DayTripRoute;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 숙소 하나 + 당일치기 (11-22 확장 5단계) */
class DayTripsTest {

    private static Trip osaka(City... dayTrips) {
        return Trip.builder().baseCity(City.OSAKA).region(City.OSAKA.region())
                .dayTripCities(new ArrayList<>(List.of(dayTrips))).build();
    }

    @Test
    void 당일치기_날은_첫날과_마지막_날을_빼고_가운데에_고르게_놓는다() {
        assertThat(DayTrips.positions(4, 1)).containsExactly(3);
        assertThat(DayTrips.positions(3, 1)).containsExactly(2);
        assertThat(DayTrips.positions(5, 2)).containsExactly(2, 4);
        assertThat(DayTrips.positions(4, 2)).containsExactly(2, 3);
        assertThat(DayTrips.positions(7, 3)).containsExactly(2, 4, 6);
    }

    @Test
    void 날짜가_모자라면_앞의_도시부터_넣고_나머지는_안내한다() {
        DayTrips.Assignment a = DayTrips.assign(osaka(City.KYOTO, City.NARA), 3);

        assertThat(a.byDay()).containsExactlyEntriesOf(java.util.Map.of(2, City.KYOTO));
        assertThat(a.skipped()).singleElement().asString().contains("나라 당일치기는 넣지 못했어요");
    }

    @Test
    void 숙소_도시에서_갈_수_없는_도시는_넣지_않고_안내한다() {
        DayTrips.Assignment a = DayTrips.assign(osaka(City.SAPPORO, City.KYOTO), 4);

        assertThat(a.byDay()).containsExactlyEntriesOf(java.util.Map.of(3, City.KYOTO));
        assertThat(a.skipped()).singleElement().asString().startsWith("삿포로는 숙소가 있는 오사카에서");
    }

    @Test
    void 이틀_여행이나_당일치기가_없으면_배정하지_않는다() {
        assertThat(DayTrips.assign(osaka(City.KYOTO), 2).count()).isZero();
        assertThat(DayTrips.assign(osaka(), 5).byDay()).isEmpty();
    }

    @Test
    void 그날_지역이_다른_도시면_당일치기이고_출발점은_도착역이다() {
        Trip trip = osaka(City.KYOTO);
        TravelPoint hotel = new TravelPoint("난바 호텔", 34.665, 135.501);

        assertThat(DayTrips.routeOf(trip, TripZone.GION)).contains(DayTripRoute.OSAKA_KYOTO);
        assertThat(DayTrips.startPoint(trip, TripZone.GION, hotel).name()).isEqualTo("교토역");
        assertThat(DayTrips.routeOf(trip, TripZone.NAMBA)).isEmpty();
        assertThat(DayTrips.startPoint(trip, TripZone.NAMBA, hotel)).isSameAs(hotel);
        assertThat(DayTrips.startPoint(trip, null, hotel)).isSameAs(hotel);
    }

    @Test
    void 시간대는_아침에_숙소를_나서_이동한_만큼_늦게_시작하고_돌아올_만큼_일찍_끝난다() {
        DayWindow base = DayWindow.of(false, false, null, null, false);   // 09:30 ~ 21:00

        // 교토: 숙소 → 역 20분 + 28분 = 48분 → 8:30 출발이면 9:18 도착이라 원래 시작 9:30, 끝은 21:00 − 48분
        DayWindow kyoto = DayTrips.window(base, DayTripRoute.OSAKA_KYOTO);
        assertThat(kyoto.start()).isEqualTo(LocalTime.of(9, 30));
        assertThat(kyoto.end()).isEqualTo(LocalTime.of(20, 12));
        assertThat(kyoto.dinner()).isTrue();

        // 유후인: 20 + 125 = 145분 → 10:55 ~ 18:35, 저녁 없음
        DayWindow yufuin = DayTrips.window(base, DayTripRoute.FUKUOKA_YUFUIN);
        assertThat(yufuin.start()).isEqualTo(LocalTime.of(10, 55));
        assertThat(yufuin.end()).isEqualTo(LocalTime.of(18, 35));
        assertThat(yufuin.lunch()).isTrue();
        assertThat(yufuin.dinner()).isFalse();
    }

    @Test
    void 안내_문장과_앞뒤_이동_단계() {
        String note = DayTrips.note(City.OSAKA, City.KYOTO, DayTripRoute.OSAKA_KYOTO, true);
        assertThat(note).isEqualTo("교토 당일치기: 숙소 → 오사카역 → JR 신쾌속 약 28분 → 교토역 (편도 580엔, 1인). "
                + "오가는 데 하루 약 1시간 36분이 걸려요.");
        assertThat(DayTrips.note(City.FUKUOKA, City.YUFUIN, DayTripRoute.FUKUOKA_YUFUIN, false))
                .contains("고속버스는 미리 예약해 두세요.", "저녁은 후쿠오카에 돌아가서 드세요.");

        List<AirportPlan.Step> steps = DayTrips.steps(osaka(City.KYOTO), TripZone.GION,
                LocalTime.of(9, 30), LocalTime.of(20, 0));
        assertThat(steps).extracting(AirportPlan.Step::kind).containsExactly("DAY_TRIP_GO", "DAY_TRIP_BACK");
        assertThat(steps.get(0).time()).isEqualTo(LocalTime.of(8, 42));
        assertThat(steps.get(0).label()).isEqualTo("숙소 → 오사카역 → 교토역 (JR 신쾌속 약 28분, 편도 580엔)");
        assertThat(steps.get(1).label()).startsWith("교토역 → 오사카역 → 숙소");
        assertThat(DayTrips.steps(osaka(City.KYOTO), TripZone.NAMBA, LocalTime.of(9, 30), null)).isEmpty();
    }

    @Test
    void 받침에_따라_은_는() {
        assertThat(DayTrips.topic("교토")).isEqualTo("교토는");
        assertThat(DayTrips.topic("도쿄역")).isEqualTo("도쿄역은");
    }

    @Test
    void 당일치기_관광지는_여유롭게_2곳_보통과_알차게_3곳() {
        assertThat(com.example.japantravelplanner.trip.entity.TravelPace.RELAXED.dayTripAttractions()).isEqualTo(2);
        assertThat(com.example.japantravelplanner.trip.entity.TravelPace.NORMAL.dayTripAttractions()).isEqualTo(3);
        assertThat(com.example.japantravelplanner.trip.entity.TravelPace.FULL.dayTripAttractions()).isEqualTo(3);
    }
}
