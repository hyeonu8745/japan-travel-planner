package com.example.japantravelplanner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupResponseDto {
    private Long id;
    private String email;
    private String nickname;
}