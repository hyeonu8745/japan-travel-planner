package com.example.japantravelplanner.itinerary.controller;

import com.example.japantravelplanner.itinerary.service.ItineraryPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 일정 생성 단계별 확인용 임시 API. 일정 생성이 완성되면 삭제 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/itinerary")
public class ItineraryDevController {

    private final ItineraryPreviewService previewService;

    @GetMapping("/{tripId}/candidates")
    public List<Map<String, Object>> candidates(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return previewService.previewCandidates(userId, tripId);
    }
}