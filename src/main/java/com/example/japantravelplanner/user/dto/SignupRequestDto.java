package com.example.japantravelplanner.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class SignupRequestDto {

    @NotBlank(message = "이메일을 입력해주세요.")
    @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S(.*\\S)?$",
            message = "비밀번호는 영문과 숫자를 모두 포함하고, 앞뒤에 공백이 없어야 합니다.")
    private String password;

    @NotBlank(message = "닉네임을 입력해주세요.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
    private String nickname;

    // 입력 앞뒤 공백 제거 (복사·붙여넣기 실수 방지)
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? null : nickname.trim();
    }

    /** BCrypt는 72바이트까지만 사용하므로, 한글이 섞인 긴 비밀번호를 막는다 */
    @AssertTrue(message = "비밀번호가 너무 깁니다. 한글을 섞은 경우 더 짧게 입력해주세요.")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}