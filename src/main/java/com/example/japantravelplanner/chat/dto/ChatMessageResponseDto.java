package com.example.japantravelplanner.chat.dto;

import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;

import java.time.LocalDateTime;

/**
 * 대화 기록 한 줄 (누가, 무엇을, 언제).
 */
public record ChatMessageResponseDto(Long id, ChatRole role, String content, LocalDateTime createdAt) {

    /** 메시지 엔티티 → 응답 */
    public static ChatMessageResponseDto from(ChatMessage message) {
        return new ChatMessageResponseDto(
                message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}