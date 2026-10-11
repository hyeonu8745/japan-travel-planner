package com.example.japantravelplanner.chat.dto;

import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.trip.entity.TripStatus;

/**
 * 메시지 전송 결과: 방금 저장된 두 메시지 + 생성 준비 상태
 * @param mode               PREFERENCE(일정 만들기 전 취향 대화) 또는 EDIT(일정을 만든 뒤 채팅으로 수정, 10-11 추가)
 * @param awaitingConfirmation EDIT에서 "이렇게 바꿀까요?"나 "어느 곳으로 할까요?"를 물어 답을 기다리는 중인지
 * @param itinerary          EDIT에서 이번 메시지로 일정이 바뀌었으면 새 버전 전체 일정 (바뀌지 않았으면 null)
 */
public record ChatReplyResponseDto(
        ChatMessageResponseDto userMessage,
        ChatMessageResponseDto assistantMessage,
        boolean readyToGenerate,
        boolean userWantsGeneration,
        TripStatus tripStatus,
        String mode,
        boolean awaitingConfirmation,
        ItineraryResponseDto itinerary
) {

    public static final String MODE_PREFERENCE = "PREFERENCE";
    public static final String MODE_EDIT = "EDIT";

    /** 취향 대화 결과 */
    public ChatReplyResponseDto(ChatMessageResponseDto userMessage, ChatMessageResponseDto assistantMessage,
                                boolean readyToGenerate, boolean userWantsGeneration, TripStatus tripStatus) {
        this(userMessage, assistantMessage, readyToGenerate, userWantsGeneration, tripStatus,
                MODE_PREFERENCE, false, null);
    }
}