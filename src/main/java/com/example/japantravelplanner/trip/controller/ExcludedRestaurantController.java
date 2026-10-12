package com.example.japantravelplanner.trip.controller;

import com.example.japantravelplanner.trip.service.ExcludedRestaurantService;
import com.example.japantravelplanner.trip.service.ExcludedRestaurantService.ExcludedRestaurantDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사용자가 이 여행에서 바꿔서 다시 추천하지 않는 식당 (10-12) */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/excluded-restaurants")
public class ExcludedRestaurantController {

    private final ExcludedRestaurantService service;

    @GetMapping
    public List<ExcludedRestaurantDto> list(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return service.list(userId, tripId);
    }

    /** "다시 추천해도 돼요" */
    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<Void> allow(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                      @PathVariable Long restaurantId) {
        service.allow(userId, tripId, restaurantId);
        return ResponseEntity.noContent().build();
    }
}
