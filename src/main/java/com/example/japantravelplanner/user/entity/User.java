package com.example.japantravelplanner.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 회원 ({@code users}): 이메일(소문자로 정규화, 유일), 비밀번호 BCrypt 해시, 닉네임.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호 저장

    @Column(nullable = false)
    private String nickname;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 처음 저장할 때 가입 시각을 넣는다 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}