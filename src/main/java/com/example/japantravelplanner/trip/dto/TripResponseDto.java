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
import java.util.List;

@Getter
@Builder
@JsonPropertyOrder({
        "id", "title", "status", "startDate", "endDate", "travelers", "budget",
        "travelPace", "interests", "transportMode", "accommodationName",
        "accommodationAddress", "allergies", "mobilityConstraints"
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
                .interests(trip.getInterests())
                .transportMode(trip.getTransportMode())
                .accommodationName(trip.getAccommodationName())
                .accommodationAddress(trip.getAccommodationAddress())
                .allergies(trip.getAllergies())
                .mobilityConstraints(trip.getMobilityConstraints())
                .build();
    }
}