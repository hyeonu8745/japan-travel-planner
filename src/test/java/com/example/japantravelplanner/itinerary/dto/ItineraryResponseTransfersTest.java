package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 10-15: 응답의 days[].transfers — 첫날 공항 → 숙소, 마지막 날 체크아웃 … 숙소 → 공항 */
class ItineraryResponseTransfersTest {

    private static ItineraryDay day(int n, LocalTime start) {
        return ItineraryDay.create(n, LocalDate.of(2026, 10, 24 + n), TripZone.SHINJUKU, start, LocalTime.of(21, 0),
                null, List.of(), List.of());
    }

    private final Trip trip = Trip.builder().arrivalAirport("HND").arrivalFlightTime(LocalTime.of(10, 30))
            .departureAirport("NRT").departureFlightTime(LocalTime.of(19, 0)).build();

    @Test
    void 첫날과_마지막_날에만_붙고_가운데_날은_비어_있다() {
        assertThat(ItineraryResponseDto.transfersOf(trip, day(1, LocalTime.of(12, 35)), 3))
                .extracting(ItineraryResponseDto.TransferDto::kind)
                .containsExactly("FLIGHT_ARRIVAL", "AIRPORT_TO_HOTEL", "HOTEL_DROP");
        assertThat(ItineraryResponseDto.transfersOf(trip, day(2, LocalTime.of(9, 30)), 3)).isEmpty();
        assertThat(ItineraryResponseDto.transfersOf(trip, day(3, LocalTime.of(9, 30)), 3))
                .extracting(ItineraryResponseDto.TransferDto::kind)
                .containsExactly("CHECKOUT", "HOTEL_PICKUP", "HOTEL_TO_AIRPORT", "FLIGHT_DEPARTURE");
    }

    @Test
    void 당일치기면_둘_다_붙고_비행기_시각이_없으면_비어_있다() {
        assertThat(ItineraryResponseDto.transfersOf(trip, day(1, LocalTime.of(12, 35)), 1)).hasSize(7);
        assertThat(ItineraryResponseDto.transfersOf(Trip.builder().build(), day(1, LocalTime.of(9, 30)), 1)).isEmpty();
    }
}
