package com.example.japantravelplanner.trip.dto;

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
        "arrivalAirport", "arrivalFlightTime", "departureAirport", "departureFlightTime"
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
                .build();
    }
}