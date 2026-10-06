package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.itinerary.entity.ItinerarySource;

import java.time.LocalDateTime;

/**
 * 변경 이력 목록의 한 줄 (일정 한 버전).
 *
 * @param changeNote 무엇이 바뀌었는지 한 줄 설명. 이 기능 이전에 만든 일정은 null이므로 source로 대신 안내할 것
 * @param latest     지금 보고 있는 최신 버전인지 (최신 버전만 고칠 수 있다)
 */
public record ItineraryVersionDto(
        int version,
        ItinerarySource source,
        LocalDateTime createdAt,
        String changeNote,
        boolean latest
) {
}