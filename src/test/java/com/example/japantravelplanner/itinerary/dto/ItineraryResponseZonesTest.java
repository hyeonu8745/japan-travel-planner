package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 하루에 두 지역을 돌면 응답의 zones에 둘 다 (10-11) */
class ItineraryResponseZonesTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private static ItineraryStop stop(int order, String name, double lat, double lng) {
        Attraction a = Attraction.builder().id((long) order).name(name).types("park")
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
        return ItineraryStop.builder().stopOrder(order).type(StopType.ATTRACTION).attraction(a)
                .arrivalTime(LocalTime.of(10, 0)).departureTime(LocalTime.of(11, 0)).stayMinutes(60)
                .travelMode(LegMode.TRANSIT).travelMinutes(15).travelDistanceMeters(2000).build();
    }

    @Test
    void 관광지_위치로_그날_도는_지역을_중심_지역부터_순서대로_모은다() {
        ItineraryDay day = ItineraryDay.create(1, LocalDate.of(2026, 10, 13), TripZone.ASAKUSA_UENO,
                null, null, null, List.of(), List.of());
        day.addStop(stop(1, "秋葉原電気街", 35.6998, 139.7713));
        day.addStop(stop(2, "浅草寺", 35.7148, 139.7967));
        day.addStop(stop(3, "神田明神", 35.7020, 139.7679));

        assertThat(ItineraryResponseDto.zonesOf(day))
                .containsExactly(TripZone.ASAKUSA_UENO, TripZone.AKIHABARA_KANDA);
        assertThat(TripZone.labelOf(ItineraryResponseDto.zonesOf(day))).isEqualTo("아사쿠사·우에노 + 아키하바라·간다");
    }
}
