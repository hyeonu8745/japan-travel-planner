package com.example.japantravelplanner.trip.controller;

import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public TripResponseDto createTrip(@AuthenticationPrincipal Long userId,
                                      @RequestBody TripRequestDto request) {
        return tripService.createTrip(userId, request);
    }

    @GetMapping
    public List<TripResponseDto> getMyTrips(@AuthenticationPrincipal Long userId) {
        return tripService.getMyTrips(userId);
    }

    @GetMapping("/{tripId}")
    public TripResponseDto getTrip(@PathVariable Long tripId) {
        return tripService.getTrip(tripId);   // 소유자 확인은 다음 단계(#1)에서
    }
}