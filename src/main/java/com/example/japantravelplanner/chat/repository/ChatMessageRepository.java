package com.example.japantravelplanner.chat.repository;

import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 대화 이력 전체 (화면 표시용)
    List<ChatMessage> findByTripIdOrderByIdAsc(Long tripId);

    // 최근 10개 (AI에게 보낼 맥락용, 최신순으로 가져와서 뒤집어 사용)
    List<ChatMessage> findTop10ByTripIdOrderByIdDesc(Long tripId);

    // 사용자별 최근 메시지 수 (호출 제한용, #30)
    long countByTripUserIdAndRoleAndCreatedAtAfter(Long userId, ChatRole role, LocalDateTime after);
}