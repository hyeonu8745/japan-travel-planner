package com.example.japantravelplanner.poster;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 포스터 묶음 (10-12).
 *
 * @param itineraryVersion 어느 일정 버전으로 만들었는지
 * @param stale            그 뒤 일정이 바뀌었는지 (true면 "일정이 바뀌었어요, 다시 만들까요?" 안내)
 * @param remainingToday   이 여행에서 24시간 안에 더 만들 수 있는 횟수
 */
public record PosterResponseDto(Long id, Long tripId, PosterStatus status, int itineraryVersion, boolean stale,
                                String errorMessage, LocalDateTime createdAt, LocalDateTime finishedAt,
                                int remainingToday, List<PageDto> pages) {

    /** @param imageUrl 다 그려졌을 때만. 로그인 토큰이 필요하므로 화면은 fetch로 받아 blob URL로 보여준다 */
    public record PageDto(int pageNo, PosterPageKind kind, PosterStatus status, String size, String imageUrl) {
    }
}
