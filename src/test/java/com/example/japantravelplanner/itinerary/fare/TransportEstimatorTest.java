package com.example.japantravelplanner.itinerary.fare;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 일정 → 교통비 추정 (10-16) */
class TransportEstimatorTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double WALK_LIMIT = 30 * 80 / 1.25;   // 1,920m
    private final FareProperties props = new FareProperties();

    // 숙소: 신주쿠 / 마지막 장소: 아사쿠사(약 8.6km) 또는 숙소 옆
    private static final double HOTEL_LAT = 35.6938, HOTEL_LNG = 139.7034;

    private static ItineraryStop stop(int order, LegMode mode, int meters, double lat, double lng) {
        return ItineraryStop.builder().stopOrder(order).type(StopType.ATTRACTION)
                .attraction(Attraction.builder().id((long) order).name("場所" + order)
                        .location(GF.createPoint(new Coordinate(lng, lat))).build())
                .arrivalTime(LocalTime.of(10, 0)).departureTime(LocalTime.of(11, 0)).stayMinutes(60)
                .travelMode(mode).travelMinutes(10).travelDistanceMeters(meters).build();
    }

    private static Itinerary itinerary(Trip trip, List<List<ItineraryStop>> days) {
        Itinerary itinerary = Itinerary.create(trip, 1, 9.0);
        for (int i = 0; i < days.size(); i++) {
            ItineraryDay day = ItineraryDay.create(i + 1, LocalDate.of(2026, 11, 1 + i), null,
                    LocalTime.of(9, 30), LocalTime.of(21, 0), "", List.of(), List.of());
            days.get(i).forEach(day::addStop);
            itinerary.addDay(day);
        }
        return itinerary;
    }

    private static Trip.TripBuilder trip() {
        return Trip.builder().id(1L).travelers(2).transportMode(TransportMode.PUBLIC_TRANSIT)
                .accommodationLocation(GF.createPoint(new Coordinate(HOTEL_LNG, HOTEL_LAT)));
    }

    @Test
    void 대중교통_구간과_멀리서_숙소로_돌아가는_구간에_거리별_운임을_더하고_도보는_뺀다() {
        Trip trip = trip().build();
        Itinerary itinerary = itinerary(trip, List.of(List.of(
                stop(1, LegMode.TRANSIT, 5000, 35.70, 139.77),      // 메트로 178 / 도에이 220
                stop(2, LegMode.WALK, 600, 35.71, 139.79),
                stop(3, LegMode.TRANSIT, 13000, 35.7148, 139.7967)  // 메트로 252 / 도에이 272, 아사쿠사에서 끝
        )));

        TransportEstimate t = TransportEstimator.estimate(trip, itinerary, props, 9.0, 2, WALK_LIMIT, 1.3);

        // 숙소로 돌아가기: 직선 약 8.6km × 1.3 ≈ 11.2km → 메트로 252 / 도에이 272
        assertThat(t.subway().legs()).isEqualTo(3);
        assertThat(t.subway().minYen()).isEqualTo(178 + 252 + 252);
        assertThat(t.subway().maxYen()).isEqualTo(220 + 272 + 272);
        assertThat(t.subway().passes()).isEmpty();                 // 764엔 < 24시간권 1,000엔
        assertThat(t.perPerson().minYen()).isEqualTo(682);
        assertThat(t.total().maxYen()).isEqualTo(764 * 2);
        assertThat(t.total().maxKrw()).isEqualTo(Math.round(764 * 2 * 9.0));
        assertThat(t.airport()).isNull();
        assertThat(t.note()).contains("갈아타거나");
    }

    @Test
    void 숙소_가까이에서_끝나면_돌아가는_구간은_걷는다() {
        Trip trip = trip().build();
        Itinerary itinerary = itinerary(trip, List.of(List.of(stop(1, LegMode.TRANSIT, 3000, 35.6950, 139.7000))));

        assertThat(TransportEstimator.estimate(trip, itinerary, props, 9.0, 1, WALK_LIMIT, 1.3).subway().legs())
                .isEqualTo(1);
    }

    @Test
    void 많이_타는_날이_이어지면_1일권을_안내하고_요금과_1일권_중_싼_쪽으로() {
        Trip trip = trip().build();
        List<ItineraryStop> busy = List.of(
                stop(1, LegMode.TRANSIT, 12000, 35.69, 139.70), stop(2, LegMode.TRANSIT, 12000, 35.69, 139.70),
                stop(3, LegMode.TRANSIT, 12000, 35.69, 139.70), stop(4, LegMode.TRANSIT, 12000, 35.6940, 139.7030));
        Itinerary itinerary = itinerary(trip, List.of(busy, busy));

        TransportEstimate t = TransportEstimator.estimate(trip, itinerary, props, 9.0, 1, WALK_LIMIT, 1.3);

        // 하루 메트로 252×4 = 1,008 / 도에이 272×4 = 1,088 → 이틀 2,176 > 48시간권 1,500
        assertThat(t.subway().passes()).containsExactly(new TransitFare.Pass(1, 2, 48, 1500));
        assertThat(t.subway().savingsYen()).isEqualTo(2176 - 1500);
        assertThat(t.subway().finalMinYen()).isEqualTo(1500);
        assertThat(t.subway().finalMaxYen()).isEqualTo(1500);
    }

    @Test
    void 비행기_시각이_있으면_공항_요금을_저렴한_방법부터_빠른_방법까지_더한다() {
        Trip trip = trip().arrivalAirport("NRT").arrivalFlightTime(LocalTime.of(10, 0))
                .departureAirport("HND").departureFlightTime(LocalTime.of(18, 0)).build();
        Itinerary itinerary = itinerary(trip, List.of(List.of(stop(1, LegMode.WALK, 500, 35.6940, 139.7030))));

        TransportEstimate t = TransportEstimator.estimate(trip, itinerary, props, 9.0, 1, WALK_LIMIT, 1.3);

        assertThat(t.airport().arrival().airport()).isEqualTo("나리타 국제공항");
        assertThat(t.airport().arrival().fastLabel()).contains("스카이라이너");
        assertThat(t.airport().minYen()).isEqualTo(1270 + 330);
        assertThat(t.airport().maxYen()).isEqualTo(2570 + 660);
        assertThat(t.perPerson().minYen()).isEqualTo(1600);
    }

    @Test
    void 렌터카_여행은_지하철_요금을_넣지_않는다() {
        Trip trip = trip().transportMode(TransportMode.RENTAL_CAR).build();
        Itinerary itinerary = itinerary(trip, List.of(List.of(stop(1, LegMode.TRANSIT, 9000, 35.7148, 139.7967))));

        TransportEstimate t = TransportEstimator.estimate(trip, itinerary, props, 9.0, 1, WALK_LIMIT, 1.3);

        assertThat(t.rentalCar()).isTrue();
        assertThat(t.subway().legs()).isZero();
        assertThat(t.perPerson().maxYen()).isZero();
    }
}
