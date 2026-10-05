package com.example.japantravelplanner.itinerary.dto;

import jakarta.validation.constraints.NotNull;

/** 일정의 식당(점심·저녁)을 다른 식당으로 바꾸는 요청 */
public record ReplaceStopRequestDto(
        @NotNull(message = "바꿀 식당을 선택해주세요.") Long restaurantId
) {
}