package com.example.japantravelplanner.trip.controller;

import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public TripResponseDto createTrip(@RequestBody TripRequestDto request) {
        Long userId = getCurrentUserId();
        return tripService.createTrip(userId, request);
    }

    @GetMapping
    public List<TripResponseDto> getMyTrips() {
        Long userId = getCurrentUserId();
        return tripService.getMyTrips(userId);
    }

    @GetMapping("/{tripId}")
    public TripResponseDto getTrip(@PathVariable Long tripId) {
        return tripService.getTrip(tripId);
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}