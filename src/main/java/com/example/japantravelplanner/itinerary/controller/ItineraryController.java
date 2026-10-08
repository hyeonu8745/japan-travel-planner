package com.example.japantravelplanner.itinerary.controller;

import com.example.japantravelplanner.itinerary.dto.AddAttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.AddAttractionRequestDto;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryVersionDto;
import com.example.japantravelplanner.itinerary.dto.ReplaceAttractionRequestDto;
import com.example.japantravelplanner.itinerary.dto.ReorderStopsRequestDto;
import com.example.japantravelplanner.itinerary.dto.ReplaceStopRequestDto;
import com.example.japantravelplanner.itinerary.dto.StopCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.service.ItineraryAttractionEditService;
import com.example.japantravelplanner.itinerary.service.ItineraryEditService;
import com.example.japantravelplanner.itinerary.service.ItineraryGenerationService;
import com.example.japantravelplanner.itinerary.service.ItineraryStopEditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/itinerary")
public class ItineraryController {

    private final ItineraryGenerationService generationService;
    private final ItineraryEditService editService;
    private final ItineraryAttractionEditService attractionEditService;
    private final ItineraryStopEditService stopEditService;

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

    /** 관광지 항목(낮 관광지 또는 저녁 뒤 야경 장소) 하나를 바꿀 때 고를 수 있는 후보 */
    @GetMapping("/stops/{stopId}/attraction-candidates")
    public AttractionCandidatesResponseDto attractionCandidates(@AuthenticationPrincipal Long userId,
                                                                @PathVariable Long tripId,
                                                                @PathVariable Long stopId) {
        return attractionEditService.candidates(userId, tripId, stopId);
    }

    /** 관광지 교체. 새 버전(EDITED)으로 저장하고 전체 일정을 돌려준다 */
    @PostMapping("/stops/{stopId}/replace-attraction")
    public ItineraryResponseDto replaceAttraction(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long tripId,
                                                  @PathVariable Long stopId,
                                                  @Valid @RequestBody ReplaceAttractionRequestDto request) {
        return attractionEditService.replace(userId, tripId, stopId, request.attractionId());
    }

    /** 항목 하나(관광지·점심·저녁) 삭제. 새 버전(EDITED)으로 저장하고 전체 일정을 돌려준다 */
    @DeleteMapping("/stops/{stopId}")
    public ItineraryResponseDto deleteStop(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long tripId,
                                           @PathVariable Long stopId) {
        return stopEditService.deleteStop(userId, tripId, stopId);
    }

    /** 같은 날 안에서 항목 순서 바꾸기 (그날의 모든 항목 id를 원하는 순서대로). 새 버전(EDITED)으로 저장 */
    @PutMapping("/days/{dayNumber}/order")
    public ItineraryResponseDto reorder(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long tripId,
                                        @PathVariable int dayNumber,
                                        @Valid @RequestBody ReorderStopsRequestDto request) {
        return stopEditService.reorder(userId, tripId, dayNumber, request.stopIds(), request.version());
    }

    /** 그날 position 자리(0이면 맨 앞)에 넣을 수 있는 관광지 후보 */
    @GetMapping("/days/{dayNumber}/attraction-candidates")
    public AddAttractionCandidatesResponseDto addCandidates(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long tripId,
                                                            @PathVariable int dayNumber,
                                                            @RequestParam int position) {
        return stopEditService.addCandidates(userId, tripId, dayNumber, position);
    }

    /** 그날 position 자리에 관광지 넣기. 새 버전(EDITED)으로 저장하고 전체 일정을 돌려준다 */
    @PostMapping("/days/{dayNumber}/attractions")
    public ItineraryResponseDto addAttraction(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long tripId,
                                              @PathVariable int dayNumber,
                                              @Valid @RequestBody AddAttractionRequestDto request) {
        return stopEditService.addAttraction(userId, tripId, dayNumber, request.attractionId(),
                request.position(), request.version());
    }

    /** 변경 이력: 이 여행의 모든 버전 (최신이 먼저) */
    @GetMapping("/versions")
    public List<ItineraryVersionDto> versions(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return editService.versions(userId, tripId);
    }

    /** 특정 버전의 일정 (보기 전용) */
    @GetMapping("/versions/{versionNo}")
    public ItineraryResponseDto version(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long tripId,
                                        @PathVariable int versionNo) {
        return editService.version(userId, tripId, versionNo);
    }

    /** 이 버전으로 되돌리기. 새 버전(RESTORED)으로 저장하고 전체 일정을 돌려준다 */
    @PostMapping("/versions/{versionNo}/restore")
    public ItineraryResponseDto restore(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long tripId,
                                        @PathVariable int versionNo) {
        return editService.restore(userId, tripId, versionNo);
    }
}