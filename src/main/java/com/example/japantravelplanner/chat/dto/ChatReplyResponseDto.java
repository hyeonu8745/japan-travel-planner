package com.example.japantravelplanner.chat.dto;

import com.example.japantravelplanner.trip.entity.TripStatus;

/** 메시지 전송 결과: 방금 저장된 두 메시지 + 생성 준비 상태 */
public record ChatReplyResponseDto(
        ChatMessageResponseDto userMessage,
        ChatMessageResponseDto assistantMessage,
        boolean readyToGenerate,
        boolean userWantsGeneration,
        TripStatus tripStatus
) {
}