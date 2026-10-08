package com.example.japantravelplanner.itinerary.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 하루 일정에 관광지를 끼워 넣는 요청.
 *
 * @param attractionId 넣을 관광지 (추가 후보 목록에서 고른 것)
 * @param position     넣을 자리 (0이면 맨 앞, 그날 항목 수와 같으면 맨 뒤)
 * @param version      화면에 보고 있는 일정 버전 (선택). 최신이 아니면 409
 */
public record AddAttractionRequestDto(
        @NotNull(message = "넣을 관광지를 선택해주세요.") Long attractionId,
        @NotNull(message = "넣을 자리를 정해주세요.") @Min(value = 0, message = "넣을 자리가 올바르지 않습니다.") Integer position,
        Integer version
) {
}
