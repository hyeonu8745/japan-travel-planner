package com.example.japantravelplanner.poster;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 여행 포스터 (10-12). 그리는 데 장당 수십 초 → POST는 202로 바로 돌려주고 GET으로 상태 확인 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/posters")
public class PosterController {

    private final PosterService posterService;

    @PostMapping
    public ResponseEntity<PosterResponseDto> start(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(posterService.start(userId, tripId));
    }

    @GetMapping("/latest")
    public PosterResponseDto latest(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return posterService.get(userId, tripId);
    }

    @GetMapping("/{posterId}/pages/{pageNo}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                        @PathVariable Long posterId, @PathVariable int pageNo) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(posterService.image(userId, tripId, posterId, pageNo));
    }
}
