package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.Region;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 여행 응답의 권역·숙소 도시·당일치기 도시 (11-22) */
class TripResponseDayTripTest {

    @Test
    void 권역과_숙소_도시와_당일치기_도시를_담고_지금_도시에서_갈_수_없는_도시는_뺀다() {
        Trip trip = Trip.builder().baseCity(City.OSAKA).region(Region.KANSAI)
                .dayTripCities(new ArrayList<>(List.of(City.NARA, City.SAPPORO))).build();

        TripResponseDto dto = TripResponseDto.from(trip);

        assertThat(dto.getRegion()).isEqualTo("KANSAI");
        assertThat(dto.getRegionLabel()).isEqualTo("간사이");
        assertThat(dto.getBaseCity()).isEqualTo("OSAKA");
        assertThat(dto.getBaseCityLabel()).isEqualTo("오사카");
        assertThat(dto.getDayTripCities()).containsExactly(
                new TripResponseDto.DayTripCityDto("NARA", "나라", 39, 680, "긴테쓰 나라선 쾌속급행", "오사카난바역", "긴테쓰나라역"));
    }

    @Test
    void 예전_여행은_수도권_도쿄_당일치기_없음() {
        TripResponseDto dto = TripResponseDto.from(Trip.builder().build());

        assertThat(dto.getRegion()).isEqualTo("KANTO");
        assertThat(dto.getBaseCity()).isEqualTo("TOKYO");
        assertThat(dto.getDayTripCities()).isEmpty();
    }
}
