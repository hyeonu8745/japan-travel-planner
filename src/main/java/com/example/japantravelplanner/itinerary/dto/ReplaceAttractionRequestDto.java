package com.example.japantravelplanner.itinerary.dto;

import jakarta.validation.constraints.NotNull;

/** 일정의 관광지(낮 관광지 또는 저녁 뒤 야경 장소)를 다른 관광지로 바꾸는 요청 */
public record ReplaceAttractionRequestDto(
        @NotNull(message = "바꿀 관광지를 선택해주세요.") Long attractionId
) {
}