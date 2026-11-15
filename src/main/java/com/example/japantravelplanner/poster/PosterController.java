package com.example.japantravelplanner.poster;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 여행 포스터. 그리는 데 장당 수십 초 → POST는 202로 바로 돌려주고 GET으로 상태 확인 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/posters")
public class PosterController {

    private final PosterService posterService;

    /** 포스터 만들기 시작 → 202 (그리는 건 따로, 상태는 GET /latest 로) */
    @PostMapping
    public ResponseEntity<PosterResponseDto> start(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                   @RequestBody(required = false) PosterRequestDto request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(posterService.start(userId, tripId, request));
    }

    /** 가장 최근 포스터와 장마다 상태 (그리는 중 / 완료 / 실패) */
    @GetMapping("/latest")
    public PosterResponseDto latest(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return posterService.get(userId, tripId);
    }

    /** 다 그려진 장의 PNG */
    @GetMapping("/{posterId}/pages/{pageNo}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                        @PathVariable Long posterId, @PathVariable int pageNo) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(posterService.image(userId, tripId, posterId, pageNo));
    }
}
