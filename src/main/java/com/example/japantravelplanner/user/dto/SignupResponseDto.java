package com.example.japantravelplanner.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 가입 결과 (비밀번호는 돌려주지 않는다).
 */
@Getter
@Builder
public class SignupResponseDto {
    private Long id;
    private String email;
    private String nickname;
}