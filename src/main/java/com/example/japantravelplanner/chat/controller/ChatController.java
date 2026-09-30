package com.example.japantravelplanner.chat.controller;

import com.example.japantravelplanner.chat.dto.ChatMessageRequestDto;
import com.example.japantravelplanner.chat.dto.ChatMessageResponseDto;
import com.example.japantravelplanner.chat.dto.ChatReplyResponseDto;
import com.example.japantravelplanner.chat.dto.PreferenceResponseDto;
import com.example.japantravelplanner.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat/messages")
    public ChatReplyResponseDto sendMessage(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long tripId,
                                            @Valid @RequestBody ChatMessageRequestDto request) {
        return chatService.sendMessage(userId, tripId, request.content().trim());
    }

    @GetMapping("/chat/messages")
    public List<ChatMessageResponseDto> getHistory(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long tripId) {
        return chatService.getHistory(userId, tripId);
    }

    @GetMapping("/preferences")
    public PreferenceResponseDto getPreference(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long tripId) {
        return chatService.getPreference(userId, tripId);
    }
}