package com.example.japantravelplanner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponseDto {
    private String accessToken;
    private Long userId;
    private String nickname;
}