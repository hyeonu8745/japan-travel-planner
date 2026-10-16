package com.example.japantravelplanner.itinerary.controller;

import com.example.japantravelplanner.itinerary.budget.BudgetSummaryDto;
import com.example.japantravelplanner.itinerary.budget.BudgetSummaryService;
import com.example.japantravelplanner.itinerary.dto.AddAttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.AddAttractionRequestDto;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryConfirmResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.RainPlanDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryVersionDto;
import com.example.japantravelplanner.itinerary.dto.ReplaceAttractionRequestDto;
import com.example.japantravelplanner.itinerary.dto.ReorderStopsRequestDto;
import com.example.japantravelplanner.itinerary.dto.ReplaceStopRequestDto;
import com.example.japantravelplanner.itinerary.dto.StopCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.naming.KoreanNameService;
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
    // 일정 응답에 한국어 이름이 비어 있으면 채운다 (예전 일정, 교체·추가로 새로 들어온 곳). 서비스 트랜잭션이 끝난 뒤라 AI 호출이 트랜잭션 밖
    private final KoreanNameService koreanNames;
    private final BudgetSummaryService budgetSummaryService;

    /** 일정 만들기 (다시 호출하면 새 버전). Terra 호출이 있어 수 초~수십 초 걸릴 수 있다 */
    @PostMapping
    public ResponseEntity<ItineraryResponseDto> generate(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long tripId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(koreanNames.fill(generationService.generate(userId, tripId)));
    }

    /** 가장 최근 버전의 일정 */
    @GetMapping
    public ItineraryResponseDto getLatest(@AuthenticationPrincipal Long userId,
                                          @PathVariable Long tripId) {
        return koreanNames.fill(generationService.getLatest(userId, tripId));
    }

    /**
     * 일정 [완성] (10-12). 확인 대기(AWAITING_CONFIRMATION)면 GENERATED로 바꾼다. 여러 번 불러도 같은 결과.
     * 일정이 없거나 조건이 바뀌어 다시 만들어야 하는 상태면 409
     */
    @PostMapping("/confirm")
    public ItineraryConfirmResponseDto confirm(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        ItineraryConfirmResponseDto confirmed = generationService.confirm(userId, tripId);
        return confirmed.withItinerary(koreanNames.fill(confirmed.itinerary()));
    }

    /** 비 오는 날 대안 미리보기 (10-16). 그날 실외 관광지를 근처 실내 장소로 바꾼 순서·시각, 저장하지 않는다 */
    @GetMapping("/days/{dayNumber}/rain-plan")
    public RainPlanDto rainPlan(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                @PathVariable int dayNumber) {
        return attractionEditService.rainPlan(userId, tripId, dayNumber);
    }

    /** 비 오는 날 대안 적용 → 새 버전(EDITED). 본문 {version}은 선택 (미리보기의 version을 보내면 그 사이 바뀌었을 때 409) */
    @PostMapping("/days/{dayNumber}/rain-plan")
    public ItineraryResponseDto applyRainPlan(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                              @PathVariable int dayNumber,
                                              @RequestBody(required = false) RainPlanDto.ApplyRequest request) {
        return koreanNames.fill(attractionEditService.applyRainPlan(userId, tripId, dayNumber,
                request == null ? null : request.version()));
    }

    /** 가장 최근 일정의 식비 요약 (10-12). 교통비·숙박비·입장료·쇼핑은 제외 */
    @GetMapping("/budget")
    public BudgetSummaryDto budget(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return budgetSummaryService.summarize(userId, tripId);
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
        return koreanNames.fill(editService.replace(userId, tripId, stopId, request.restaurantId()));
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
        return koreanNames.fill(attractionEditService.replace(userId, tripId, stopId, request.attractionId()));
    }

    /** 항목 하나(관광지·점심·저녁) 삭제. 새 버전(EDITED)으로 저장하고 전체 일정을 돌려준다 */
    @DeleteMapping("/stops/{stopId}")
    public ItineraryResponseDto deleteStop(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long tripId,
                                           @PathVariable Long stopId) {
        return koreanNames.fill(stopEditService.deleteStop(userId, tripId, stopId));
    }

    /** 같은 날 안에서 항목 순서 바꾸기 (그날의 모든 항목 id를 원하는 순서대로). 새 버전(EDITED)으로 저장 */
    @PutMapping("/days/{dayNumber}/order")
    public ItineraryResponseDto reorder(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long tripId,
                                        @PathVariable int dayNumber,
                                        @Valid @RequestBody ReorderStopsRequestDto request) {
        return koreanNames.fill(stopEditService.reorder(userId, tripId, dayNumber, request.stopIds(), request.version()));
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
        return koreanNames.fill(stopEditService.addAttraction(userId, tripId, dayNumber, request.attractionId(),
                request.position(), request.version()));
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
        return koreanNames.fill(editService.version(userId, tripId, versionNo));
    }

    /** 이 버전으로 되돌리기. 새 버전(RESTORED)으로 저장하고 전체 일정을 돌려준다 */
    @PostMapping("/versions/{versionNo}/restore")
    public ItineraryResponseDto restore(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long tripId,
                                        @PathVariable int versionNo) {
        return koreanNames.fill(editService.restore(userId, tripId, versionNo));
    }
}