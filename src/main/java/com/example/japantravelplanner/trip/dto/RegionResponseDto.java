package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.trip.entity.Airport;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.DayTripRoute;
import com.example.japantravelplanner.trip.region.Region;

import java.util.Arrays;
import java.util.List;

/**
 * 여행 조건 화면에 쓰는 권역 목록 (GET /api/regions, 11-22 확장 5단계): 권역마다 공항과 도시, 도시마다 당일치기로 갈 수 있는 도시.
 * 숙소 도시는 숙소 이름·주소나 좌표로 서버가 정한다. 화면은 권역 → 공항·당일치기 후보를 보여 줄 때 쓴다.
 */
public record RegionResponseDto(String code, String label, List<AirportDto> airports, List<CityDto> cities) {

    public record AirportDto(String code, String label) {
    }

    /** @param dayTrips 이 도시에 묵을 때 당일치기로 갈 수 있는 도시 (가까운 순) */
    public record CityDto(String code, String label, List<TripResponseDto.DayTripCityDto> dayTrips) {
    }

    /** 모든 권역 (선언 순서) */
    public static List<RegionResponseDto> all() {
        return Arrays.stream(Region.values()).map(RegionResponseDto::of).toList();
    }

    static RegionResponseDto of(Region region) {
        return new RegionResponseDto(region.name(), region.label(),
                Airport.inRegion(region).stream().map(a -> new AirportDto(a.name(), a.label())).toList(),
                City.inRegion(region).stream().map(RegionResponseDto::city).toList());
    }

    static CityDto city(City city) {
        return new CityDto(city.name(), city.label(), DayTripRoute.from(city).stream()
                .map(r -> TripResponseDto.DayTripCityDto.of(city, r.other(city), r)).toList());
    }
}
