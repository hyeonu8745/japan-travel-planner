package com.example.japantravelplanner.trip.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 여행 조건 화면용 권역 목록 GET /api/regions (11-22) */
class RegionResponseDtoTest {

    @Test
    void 권역마다_공항과_도시_도시마다_당일치기_도시() {
        List<RegionResponseDto> all = RegionResponseDto.all();
        assertThat(all).extracting(RegionResponseDto::code)
                .containsExactly("KANTO", "KANSAI", "CHUBU", "HIROSHIMA", "KYUSHU_NORTH", "HOKKAIDO", "OKINAWA");

        RegionResponseDto kansai = all.get(1);
        assertThat(kansai.label()).isEqualTo("간사이");
        assertThat(kansai.airports()).extracting(RegionResponseDto.AirportDto::code).containsExactly("KIX", "UKB");
        RegionResponseDto.CityDto osaka = kansai.cities().get(0);
        assertThat(osaka.code()).isEqualTo("OSAKA");
        assertThat(osaka.dayTrips()).extracting(TripResponseDto.DayTripCityDto::city).containsExactly("KOBE", "KYOTO", "NARA");
        TripResponseDto.DayTripCityDto kyoto = osaka.dayTrips().get(1);
        assertThat(kyoto).isEqualTo(new TripResponseDto.DayTripCityDto("KYOTO", "교토", 28, 580, "JR 신쾌속", "오사카역", "교토역"));
    }
}
