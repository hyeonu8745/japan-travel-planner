package com.example.japantravelplanner.user.token;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 갱신 토큰 쿠키 (11-15 보안 2-5): 자바스크립트가 읽을 수 없게(HttpOnly), /api/auth 요청에만 실어 보낸다.
 * 프론트는 이 쿠키를 직접 다루지 않고 요청에 credentials 만 켠다
 */
@Component
public class RefreshCookie {

    public static final String NAME = "refresh_token";
    static final String PATH = "/api/auth";

    private final RefreshTokenProperties props;

    public RefreshCookie(RefreshTokenProperties props) {
        this.props = props;
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(Duration.ofDays(props.getTtlDays())).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.isCookieSecure())
                .sameSite(props.getCookieSameSite())
                .path(PATH);
    }
}
