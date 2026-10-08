package com.example.japantravelplanner.itinerary.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 하루 안의 항목 순서를 바꾸는 요청.
 *
 * @param stopIds 그날의 모든 항목 id를 원하는 순서대로 (빠짐·중복 없이)
 * @param version 화면에 보고 있는 일정 버전 (선택). 최신이 아니면 409
 */
public record ReorderStopsRequestDto(
        @NotEmpty(message = "항목 순서를 보내주세요.") List<Long> stopIds,
        Integer version
) {
}
