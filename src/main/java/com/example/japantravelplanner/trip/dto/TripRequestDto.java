package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.trip.entity.TransportMode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class TripRequestDto {
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
}