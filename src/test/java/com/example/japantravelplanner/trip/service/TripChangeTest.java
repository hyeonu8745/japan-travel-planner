package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 여행 조건이 바뀐 항목에 따라 일정을 어떻게 맞출지 (새로 만들기 / 시각만 / 그대로) */
class TripChangeTest {

    static Trip trip() {
        return Trip.builder().id(5L)
                .startDate(LocalDate.of(2026, 10, 20)).endDate(LocalDate.of(2026, 10, 23))
                .travelers(2).budget(1_600_000).travelPace(TravelPace.NORMAL)
                .interests(List.of(Interest.FOOD, Interest.NIGHT_VIEW))
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .accommodationName("테스트 호텔").accommodationAddress("東京都新宿区歌舞伎町1-19-1")
                .build();
    }

    /** 지금 여행과 같은 값으로 채운 요청 (테스트마다 한 항목씩 바꾼다) */
    static TripRequestDto sameAs(Trip trip) {
        TripRequestDto request = new TripRequestDto();
        request.setStartDate(trip.getStartDate());
        request.setEndDate(trip.getEndDate());
        request.setTravelers(trip.getTravelers());
        request.setBudget(trip.getBudget());
        request.setTravelPace(trip.getTravelPace());
        request.setInterests(trip.getInterests());
        request.setAllergies(trip.getAllergies());
        request.setMobilityConstraints(trip.getMobilityConstraints());
        request.setTransportMode(trip.getTransportMode());
        request.setAccommodationName(trip.getAccommodationName());
        request.setAccommodationAddress(trip.getAccommodationAddress());
        request.setArrivalAirport(trip.getArrivalAirport());
        request.setArrivalFlightTime(trip.getArrivalFlightTime());
        request.setDepartureAirport(trip.getDepartureAirport());
        request.setDepartureFlightTime(trip.getDepartureFlightTime());
        return request;
    }

    private final Trip trip = trip();

    @Test
    void 아무것도_안_바꾸면_일정은_그대로() {
        TripChange change = TripChange.between(trip, sameAs(trip));

        assertThat(change.impact()).isEqualTo(TripChange.Impact.NONE);
        assertThat(change.fields()).isEmpty();
    }

    @Test
    void 날짜_인원_예산_속도_관심사_알레르기가_바뀌면_일정을_새로_만든다() {
        TripRequestDto dates = sameAs(trip);
        dates.setEndDate(LocalDate.of(2026, 10, 22));
        TripRequestDto budget = sameAs(trip);
        budget.setBudget(900_000);
        TripRequestDto pace = sameAs(trip);
        pace.setTravelPace(TravelPace.RELAXED);
        TripRequestDto interests = sameAs(trip);
        interests.setInterests(List.of(Interest.SHOPPING));
        TripRequestDto allergies = sameAs(trip);
        allergies.setAllergies("새우");
        TripRequestDto travelers = sameAs(trip);
        travelers.setTravelers(3);

        for (TripRequestDto request : List.of(dates, budget, pace, interests, allergies, travelers)) {
            assertThat(TripChange.between(trip, request).impact()).isEqualTo(TripChange.Impact.REGENERATE);
        }
        assertThat(TripChange.between(trip, dates).fields()).containsExactly("endDate");
    }

    @Test
    void 숙소_주소_이동수단_이동_제약만_바뀌면_시각만_다시_계산한다() {
        TripRequestDto request = sameAs(trip);
        request.setAccommodationAddress("東京都港区芝公園4-2-8");
        request.setTransportMode(TransportMode.RENTAL_CAR);
        request.setMobilityConstraints("무릎이 안 좋아요");

        TripChange change = TripChange.between(trip, request);

        assertThat(change.impact()).isEqualTo(TripChange.Impact.RETIME);
        assertThat(change.fields()).containsExactly("mobilityConstraints", "transportMode", "accommodationAddress");
        assertThat(change.labels()).isEqualTo("이동 제약, 이동수단, 숙소 주소");
    }

    @Test
    void 새로_만들_항목과_시각_항목이_함께_바뀌면_새로_만든다() {
        TripRequestDto request = sameAs(trip);
        request.setTransportMode(TransportMode.RENTAL_CAR);
        request.setBudget(2_000_000);

        assertThat(TripChange.between(trip, request).impact()).isEqualTo(TripChange.Impact.REGENERATE);
    }

    @Test
    void 숙소_이름만_바뀌면_일정은_그대로() {
        TripRequestDto request = sameAs(trip);
        request.setAccommodationName("다른 이름의 같은 호텔");

        TripChange change = TripChange.between(trip, request);

        assertThat(change.impact()).isEqualTo(TripChange.Impact.NONE);
        assertThat(change.fields()).containsExactly("accommodationName");
    }

    @Test
    void 관심사는_순서만_다르면_바뀐_것이_아니다() {
        TripRequestDto request = sameAs(trip);
        request.setInterests(List.of(Interest.NIGHT_VIEW, Interest.FOOD));

        assertThat(TripChange.between(trip, request).fields()).isEmpty();
    }

    @Test
    void 빈_문자열과_앞뒤_공백은_입력_안_함과_같다() {
        TripRequestDto request = sameAs(trip);
        request.setAllergies("   ");                 // 원래 null
        request.setMobilityConstraints("");
        request.setAccommodationName("  테스트 호텔 ");

        assertThat(TripChange.between(trip, request).fields()).isEmpty();
    }

    @Test
    void 관심사를_비우면_바뀐_것이다() {
        TripRequestDto request = sameAs(trip);
        request.setInterests(null);

        assertThat(TripChange.between(trip, request).fields()).containsExactly("interests");
    }

    @Test
    void 공항이나_비행기_시각이_바뀌면_시간대가_달라지므로_새로_만든다() {
        TripRequestDto request = sameAs(trip);
        request.setArrivalAirport("hnd");
        request.setArrivalFlightTime(java.time.LocalTime.of(10, 30));

        TripChange change = TripChange.between(trip, request);

        assertThat(change.impact()).isEqualTo(TripChange.Impact.REGENERATE);
        assertThat(change.fields()).containsExactly("arrivalAirport", "arrivalFlightTime");
        assertThat(change.labels()).isEqualTo("도착 공항, 비행기 도착 시각");
    }

    @Test
    void 공항_코드는_대소문자가_달라도_같은_공항() {
        trip.setDepartureAirport("NRT");
        TripRequestDto request = sameAs(trip);
        request.setDepartureAirport(" nrt ");

        assertThat(TripChange.between(trip, request).fields()).isEmpty();
    }

    @Test
    void 모르는_공항이나_공항_없는_비행기_시각은_입력_오류() {
        TripRequestDto request = sameAs(trip);
        request.setArrivalAirport("KIX");
        assertThat(request.isAirportKnown()).isFalse();

        request.setArrivalAirport(null);
        request.setArrivalFlightTime(java.time.LocalTime.of(10, 0));
        assertThat(request.isAirportKnown()).isTrue();
        assertThat(request.isAirportGivenWithTime()).isFalse();

        request.setArrivalAirport("HND");
        assertThat(request.isAirportGivenWithTime()).isTrue();
    }
}
