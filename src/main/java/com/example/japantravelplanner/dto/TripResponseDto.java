package com.example.japantravelplanner.dto;

import com.example.japantravelplanner.entity.TransportMode;
import com.example.japantravelplanner.entity.Trip;
import com.example.japantravelplanner.entity.TripStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class TripResponseDto {
    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String travelers;
    private Integer budget;
    private String travelPace;
    private List<String> interests;
    private String allergies;
    private String mobilityConstraints;
    private TransportMode transportMode;
    private String accommodationName;
    private String accommodationAddress;
    private TripStatus status;

    public static TripResponseDto from(Trip trip) {
        return TripResponseDto.builder()
                .id(trip.getId())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .travelers(trip.getTravelers())
                .budget(trip.getBudget())
                .travelPace(trip.getTravelPace())
                .interests(trip.getInterests())
                .allergies(trip.getAllergies())
                .mobilityConstraints(trip.getMobilityConstraints())
                .transportMode(trip.getTransportMode())
                .accommodationName(trip.getAccommodationName())
                .accommodationAddress(trip.getAccommodationAddress())
                .status(trip.getStatus())
                .build();
    }
}