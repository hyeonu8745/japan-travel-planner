package com.example.japantravelplanner.chat.entity;

import com.example.japantravelplanner.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 대화 메시지 ({@code chat_messages}). AI 답이면 쓴 모델 이름을, 확인을 기다리는 채팅 수정이면 그 내용(pendingEdit, JSON)을 같이 남긴다.
 */
@Entity
@Table(name = "chat_messages",
        indexes = @Index(name = "idx_chat_messages_trip", columnList = "trip_id, id"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    // AI 메시지일 때 응답한 모델 (gpt-4o-mini, gpt-5.6-terra). 사용자 메시지는 null
    @Column(length = 50)
    private String model;

    // 일정 수정 대화에서 "이렇게 바꿀까요?"라고 물은 AI 메시지에 붙는 대기 중인 변경 (JSON).
    // 사용자가 "네"나 번호로 답하면 이 값대로 적용한다. 그 밖의 메시지는 null
    @Column(name = "pending_edit", columnDefinition = "text")
    private String pendingEdit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 처음 저장할 때 보낸 시각 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}