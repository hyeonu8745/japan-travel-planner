package com.example.japantravelplanner.trip.controller;

import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripResponseDto> createTrip(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody TripRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.createTrip(userId, request));
    }

    @GetMapping
    public List<TripResponseDto> getMyTrips(@AuthenticationPrincipal Long userId) {
        return tripService.getMyTrips(userId);
    }

    @GetMapping("/{tripId}")
    public TripResponseDto getTrip(@AuthenticationPrincipal Long userId,
                                   @PathVariable Long tripId) {
        return tripService.getTrip(userId, tripId);
    }
}