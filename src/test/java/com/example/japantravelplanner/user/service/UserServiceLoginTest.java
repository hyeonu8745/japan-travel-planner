package com.example.japantravelplanner.user.service;

import com.example.japantravelplanner.global.exception.BadRequestException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.global.security.JwtTokenProvider;
import com.example.japantravelplanner.user.dto.LoginRequestDto;
import com.example.japantravelplanner.user.entity.User;
import com.example.japantravelplanner.user.repository.UserRepository;
import com.example.japantravelplanner.user.security.LoginAttemptLimiter;
import com.example.japantravelplanner.user.security.LoginAttemptProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 11-15 보안: 로그인 시도 제한(1-1)과 없는 이메일도 비밀번호 확인 한 번(2-9) */
class UserServiceLoginTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T07:00:00Z"), ZoneOffset.UTC);

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private final UserService service = new UserService(users, encoder,
            new JwtTokenProvider("test-secret-key-that-is-long-enough-for-hs256-0123456789", 3_600_000, CLOCK),
            new LoginAttemptLimiter(new LoginAttemptProperties(), CLOCK));

    private UserService withUser() {
        User user = User.builder().id(7L).email("me@x.com").password(encoder.encode("password1")).nickname("나").build();
        when(users.findByEmail("me@x.com")).thenReturn(Optional.of(user));
        return service;
    }

    private static LoginRequestDto req(String email, String password) {
        LoginRequestDto r = new LoginRequestDto();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    @Test
    void 맞으면_토큰을_주고_이메일은_공백과_대소문자를_무시한다() {
        var res = withUser().login(req(" Me@X.com ", "password1"), "1.1.1.1");
        assertThat(res.getUserId()).isEqualTo(7L);
        assertThat(res.getAccessToken()).isNotBlank();
    }

    @Test
    void 틀리면_없는_이메일과_같은_문구로_400() {
        withUser();
        when(users.findByEmail("none@x.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(req("me@x.com", "wrong"), "1.1.1.1"))
                .isInstanceOf(BadRequestException.class).hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
        assertThatThrownBy(() -> service.login(req("none@x.com", "password1"), "1.1.1.1"))
                .isInstanceOf(BadRequestException.class).hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void 없는_이메일이어도_비밀번호_확인을_한_번_한다() {
        when(users.findByEmail("none@x.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(req("none@x.com", "password1"), "1.1.1.1"));
        verify(encoder).matches(org.mockito.ArgumentMatchers.eq("password1"), anyString());
    }

    @Test
    void 다섯_번_틀리면_맞는_비밀번호도_429() {
        withUser();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(req("me@x.com", "wrong"), "1.1.1.1")).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> service.login(req("ME@x.com", "password1"), "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
        // 다른 곳(IP)에서는 그대로 로그인된다
        assertThat(service.login(req("me@x.com", "password1"), "2.2.2.2").getUserId()).isEqualTo(7L);
    }
}
