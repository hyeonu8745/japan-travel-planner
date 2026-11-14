package com.example.japantravelplanner.trip.entity;

import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.Region;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-08 리팩터링: 공항에 권역을 붙였다. 도쿄 여행은 예전과 똑같이 나리타·하네다만 받는다 */
class AirportRegionTest {

    @Test
    void 공항은_권역별로_있고_도쿄_여행은_나리타_하네다만_받는다() {
        assertThat(Airport.isKnownOrBlank("nrt", Region.KANTO)).isTrue();
        assertThat(Airport.isKnownOrBlank("HND", Region.KANTO)).isTrue();
        assertThat(Airport.isKnownOrBlank("KIX", Region.KANTO)).isFalse();
        assertThat(Airport.isKnownOrBlank("KIX", Region.KANSAI)).isTrue();
        assertThat(Airport.isKnownOrBlank("ICN", Region.KANTO)).isFalse();
        assertThat(Airport.isKnownOrBlank(" ", Region.KANTO)).isTrue();
        assertThat(Airport.isKnownOrBlank(null, Region.OKINAWA)).isTrue();
        for (Region r : Region.values()) {
            assertThat(java.util.Arrays.stream(Airport.values()).anyMatch(a -> a.region() == r)).as(r.name()).isTrue();
        }
    }

    @Test
    void 도쿄_공항_이동_시간은_예전_값_그대로이고_값이_없는_도시는_60분() {
        assertThat(Airport.NRT.transferMinutes(City.TOKYO)).isEqualTo(AirportAccess.of(Airport.NRT, City.TOKYO).orElseThrow().minutes());
        assertThat(Airport.HND.transferMinutes(City.TOKYO)).isEqualTo(AirportAccess.of(Airport.HND, City.TOKYO).orElseThrow().minutes());
        assertThat(AirportAccess.of(Airport.KIX, City.OSAKA)).isEmpty();
        assertThat(Airport.KIX.transferMinutes(City.OSAKA)).isEqualTo(60);
    }

    @Test
    void 여행은_권역과_기준_도시가_없으면_수도권_도쿄() {
        Trip trip = Trip.builder().build();
        assertThat(trip.getRegion()).isEqualTo(Region.KANTO);
        assertThat(trip.getBaseCity()).isEqualTo(City.TOKYO);
    }

    @Test
    void 여행_권역의_공항이_아니면_비행기_정보로_쓰지_않는다() {
        Trip tokyo = Trip.builder().arrivalAirport("KIX").arrivalFlightTime(LocalTime.of(10, 0))
                .departureAirport("HND").departureFlightTime(LocalTime.of(18, 0)).build();
        assertThat(tokyo.arrivalAirportInfo()).isEmpty();
        assertThat(tokyo.departureAirportInfo()).contains(Airport.HND);

        Trip osaka = Trip.builder().region(Region.KANSAI).baseCity(City.OSAKA)
                .arrivalAirport("KIX").arrivalFlightTime(LocalTime.of(10, 0)).build();
        assertThat(osaka.arrivalAirportInfo()).contains(Airport.KIX);
    }
}
