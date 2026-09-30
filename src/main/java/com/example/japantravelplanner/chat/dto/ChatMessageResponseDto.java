package com.example.japantravelplanner.chat.dto;

import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;

import java.time.LocalDateTime;

public record ChatMessageResponseDto(Long id, ChatRole role, String content, LocalDateTime createdAt) {

    public static ChatMessageResponseDto from(ChatMessage message) {
        return new ChatMessageResponseDto(
                message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}