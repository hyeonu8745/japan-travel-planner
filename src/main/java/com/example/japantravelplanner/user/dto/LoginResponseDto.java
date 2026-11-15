package com.example.japantravelplanner.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인·토큰 갱신 응답: 접근 토큰(1시간), 사용자 id, 닉네임. 갱신 토큰은 본문이 아니라 쿠키로 간다.
 */
@Getter
@Builder
public class LoginResponseDto {
    private String accessToken;
    private Long userId;
    private String nickname;
}