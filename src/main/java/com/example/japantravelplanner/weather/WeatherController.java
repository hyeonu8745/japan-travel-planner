package com.example.japantravelplanner.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 여행 날짜별 날씨 (화면 머리의 날씨 칸) */
@RestController
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    /** 여행 날짜별 날씨 (가까운 날은 예보, 먼 날은 작년 같은 날) */
    @GetMapping("/api/trips/{tripId}/weather")
    public WeatherResponseDto weather(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return weatherService.get(userId, tripId);
    }
}
