package com.example.japantravelplanner.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequestDto(
        @NotBlank(message = "메시지를 입력해주세요.")
        @Size(max = 1000, message = "메시지는 1,000자 이하여야 합니다.")
        String content
) {
}