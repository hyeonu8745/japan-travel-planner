package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.DayTripRoute;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 여행 조건 응답 (목록·조회·만들기). 엔티티의 목록은 복사해서 담는다 (트랜잭션이 끝난 뒤 JSON 으로 바꾸기 때문).
 */
@Getter
@Builder
@JsonPropertyOrder({
        "id", "title", "status", "startDate", "endDate", "travelers", "budget",
        "travelPace", "interests", "transportMode", "accommodationName",
        "accommodationAddress", "allergies", "mobilityConstraints",
        "arrivalAirport", "arrivalFlightTime", "departureAirport", "departureFlightTime",
        "region", "regionLabel", "baseCity", "baseCityLabel", "dayTripCities"
})
public class TripResponseDto {
    private Long id;
    private String title;
    private TripStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer travelers;
    private Integer budget;
    private TravelPace travelPace;
    private List<Interest> interests;
    private TransportMode transportMode;
    private String accommodationName;
    private String accommodationAddress;
    private String allergies;
    private String mobilityConstraints;
    // 공항·비행기 시각 (입력 안 했으면 null)
    private String arrivalAirport;
    private LocalTime arrivalFlightTime;
    private String departureAirport;
    private LocalTime departureFlightTime;
    // 11-22 확장 5단계: 권역·숙소 도시(일정을 만들 때 숙소 좌표로 다시 정해질 수 있다)·당일치기 도시
    private String region;
    private String regionLabel;
    private String baseCity;
    private String baseCityLabel;
    private List<DayTripCityDto> dayTripCities;

    /**
     * 당일치기 도시 하나 (숙소 도시에서 가는 방법)
     * @param minutes 역 ↔ 역 편도 시간(분), fareYen 편도 운임(엔, 1인), route 타는 것, fromStation·toStation 역 이름
     */
    public record DayTripCityDto(String city, String label, int minutes, int fareYen, String route,
                                 String fromStation, String toStation) {
        /** 숙소 도시 base 에서 target 으로 가는 경로 */
        public static DayTripCityDto of(City base, City target, DayTripRoute route) {
            return new DayTripCityDto(target.name(), target.label(), route.minutes(), route.fareYen(), route.label(),
                    route.stationOf(base).name(), route.stationOf(target).name());
        }
    }

    /** 여행 엔티티 → 응답. 트랜잭션 안에서 부른다 (관심사 목록을 복사해 담는다) */
    public static TripResponseDto from(Trip trip) {
        return TripResponseDto.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .status(trip.getStatus())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .travelers(trip.getTravelers())
                .budget(trip.getBudget())
                .travelPace(trip.getTravelPace())
                // open-in-view 끔: 엔티티의 지연 로딩 목록을 그대로 넣으면 트랜잭션이 끝난 뒤 JSON 으로 바꿀 때 실패한다 → 복사
                .interests(trip.getInterests() == null ? null : List.copyOf(trip.getInterests()))
                .transportMode(trip.getTransportMode())
                .accommodationName(trip.getAccommodationName())
                .accommodationAddress(trip.getAccommodationAddress())
                .allergies(trip.getAllergies())
                .mobilityConstraints(trip.getMobilityConstraints())
                .arrivalAirport(trip.getArrivalAirport())
                .arrivalFlightTime(trip.getArrivalFlightTime())
                .departureAirport(trip.getDepartureAirport())
                .departureFlightTime(trip.getDepartureFlightTime())
                .region(trip.getRegion().name())
                .regionLabel(trip.getRegion().label())
                .baseCity(trip.getBaseCity().name())
                .baseCityLabel(trip.getBaseCity().label())
                .dayTripCities(dayTripsOf(trip.getBaseCity(), trip.getDayTripCities()))
                .build();
    }

    /** 당일치기 도시 → 응답 (지금 숙소 도시에서 갈 수 없는 도시는 빼고) */
    static List<DayTripCityDto> dayTripsOf(City base, List<City> cities) {
        return cities.stream()
                .flatMap(c -> DayTripRoute.between(base, c).stream().map(r -> DayTripCityDto.of(base, c, r)))
                .toList();
    }
}