package com.example.japantravelplanner.itinerary.controller;

import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.service.ItineraryGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/itinerary")
public class ItineraryController {

    private final ItineraryGenerationService generationService;

    /** 일정 만들기 (다시 호출하면 새 버전). Terra 호출이 있어 수 초~수십 초 걸릴 수 있다 */
    @PostMapping
    public ResponseEntity<ItineraryResponseDto> generate(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long tripId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(generationService.generate(userId, tripId));
    }

    /** 가장 최근 버전의 일정 */
    @GetMapping
    public ItineraryResponseDto getLatest(@AuthenticationPrincipal Long userId,
                                          @PathVariable Long tripId) {
        return generationService.getLatest(userId, tripId);
    }
}