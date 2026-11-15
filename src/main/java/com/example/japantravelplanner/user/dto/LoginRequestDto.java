package com.example.japantravelplanner.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 로그인 요청 (이메일은 앞뒤 공백을 지운다).
 */
@Getter
@Setter
public class LoginRequestDto {

    @NotBlank(message = "이메일을 입력해주세요.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;

    /** 이메일 앞뒤 공백을 지운다 (대소문자 정리는 UserService 에서) */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}