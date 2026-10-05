package com.example.japantravelplanner.itinerary.controller;

import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.service.ItineraryGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.japantravelplanner.itinerary.dto.ReplaceStopRequestDto;
import com.example.japantravelplanner.itinerary.dto.StopCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.service.ItineraryEditService;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/itinerary")
public class ItineraryController {

    private final ItineraryGenerationService generationService;
    private final ItineraryEditService editService;

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

    /** 식당(점심·저녁) 항목 하나를 바꿀 때 고를 수 있는 후보 */
    @GetMapping("/stops/{stopId}/candidates")
    public StopCandidatesResponseDto candidates(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long tripId,
                                                @PathVariable Long stopId) {
        return editService.candidates(userId, tripId, stopId);
    }

    /** 식당 교체. 새 버전(EDITED)으로 저장하고 전체 일정을 돌려준다 */
    @PostMapping("/stops/{stopId}/replace")
    public ItineraryResponseDto replace(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long tripId,
                                        @PathVariable Long stopId,
                                        @Valid @RequestBody ReplaceStopRequestDto request) {
        return editService.replace(userId, tripId, stopId, request.restaurantId());
    }
}