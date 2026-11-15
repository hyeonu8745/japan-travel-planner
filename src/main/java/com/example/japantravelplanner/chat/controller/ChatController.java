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

/**
 * 대화 API. 일정 전에는 취향 대화, 일정 뒤에는 채팅 수정으로 같은 주소를 쓴다 (서버가 여행 상태로 구분).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}")
public class ChatController {

    private final ChatService chatService;

    /** 메시지 보내기 → 사용자 말과 AI 답 두 개, 일정 만들 준비 상태(취향 대화) 또는 확인 대기 변경(채팅 수정) */
    @PostMapping("/chat/messages")
    public ChatReplyResponseDto sendMessage(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long tripId,
                                            @Valid @RequestBody ChatMessageRequestDto request) {
        return chatService.sendMessage(userId, tripId, request.content().trim());
    }

    /** 대화 기록 (처음이면 서버가 첫 인사를 만들어 넣는다) */
    @GetMapping("/chat/messages")
    public List<ChatMessageResponseDto> getHistory(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long tripId) {
        return chatService.getHistory(userId, tripId);
    }

    /** 취향 대화에서 정리된 조건과 요약 */
    @GetMapping("/preferences")
    public PreferenceResponseDto getPreference(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long tripId) {
        return chatService.getPreference(userId, tripId);
    }
}