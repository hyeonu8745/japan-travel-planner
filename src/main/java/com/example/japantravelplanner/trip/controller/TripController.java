package com.example.japantravelplanner.trip.controller;

import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.dto.TripUpdateResponseDto;
import com.example.japantravelplanner.trip.service.TripEditService;
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
    private final TripEditService tripEditService;

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

    /** 여행 조건 수정 (생성과 같은 요청 형식, 전체 값을 보낸다). 일정이 있으면 바뀐 항목에 따라 일정도 맞춘다 */
    @PutMapping("/{tripId}")
    public TripUpdateResponseDto updateTrip(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long tripId,
                                            @Valid @RequestBody TripRequestDto request) {
        return tripEditService.update(userId, tripId, request);
    }

    /** 여행 삭제 (대화·취향·일정의 모든 버전까지 함께 지운다. 되돌릴 수 없다) */
    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long tripId) {
        tripEditService.delete(userId, tripId);
        return ResponseEntity.noContent().build();
    }
}