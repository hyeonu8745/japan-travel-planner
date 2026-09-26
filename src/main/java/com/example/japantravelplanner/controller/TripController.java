package com.example.japantravelplanner.controller;

import com.example.japantravelplanner.dto.TripRequestDto;
import com.example.japantravelplanner.dto.TripResponseDto;
import com.example.japantravelplanner.service.TripService;
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