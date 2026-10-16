package com.example.japantravelplanner.chat.service;

import com.example.japantravelplanner.chat.ai.PreferencePrompt;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 10-15: 여행 조건에 비행기 시각이 있으면 취향 대화에서 도착·출발 시각을 다시 묻지 않게 AI에 알려준다 */
class ChatFlightContextTest {

    @Test
    void 비행기_시각을_여행_조건_설명에_넣는다() {
        Trip both = Trip.builder().arrivalAirport("NRT").arrivalFlightTime(LocalTime.of(10, 30))
                .departureAirport("HND").departureFlightTime(LocalTime.of(18, 0)).build();
        assertThat(ChatService.describeFlights(both)).isEqualTo("도착 나리타 국제공항 10:30 / 출발 하네다 공항 18:00 (공항에는 3시간 전 도착으로 계산)");

        Trip arrivalOnly = Trip.builder().arrivalAirport("HND").arrivalFlightTime(LocalTime.of(9, 5)).build();
        assertThat(ChatService.describeFlights(arrivalOnly)).isEqualTo("도착 하네다 공항 09:05 / 출발 입력 안 함");

        assertThat(ChatService.describeFlights(Trip.builder().build())).isEqualTo("입력 안 함");
    }

    @Test
    void 프롬프트에_비행기_시각이_있으면_묻지_않는_규칙() {
        assertThat(PreferencePrompt.INSTRUCTIONS).contains("비행기에 도착(또는 출발) 시각이 있으면 그 시각은 이미 아는 것이다");
    }
}
