package com.example.japantravelplanner.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 여행 날짜별 날씨 (10-16, 화면 머리의 날씨 칸) */
@RestController
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/api/trips/{tripId}/weather")
    public WeatherResponseDto weather(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return weatherService.get(userId, tripId);
    }
}
