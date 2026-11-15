package com.example.japantravelplanner.user.token;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 갱신 토큰 쿠키: 자바스크립트가 읽을 수 없게(HttpOnly), /api/auth 요청에만 실어 보낸다.
 * 프론트는 이 쿠키를 직접 다루지 않고 요청에 credentials 만 켠다
 */
@Component
public class RefreshCookie {

    public static final String NAME = "refresh_token";
    static final String PATH = "/api/auth";

    private final RefreshTokenProperties props;

    /** @param props 쿠키 설정 (유효 기간, Secure, SameSite) */
    public RefreshCookie(RefreshTokenProperties props) {
        this.props = props;
    }

    /** 갱신 토큰을 담은 쿠키 (유효 기간 = 갱신 토큰 기간) */
    public ResponseCookie issue(String token) {
        return base(token).maxAge(Duration.ofDays(props.getTtlDays())).build();
    }

    /** 쿠키를 지우는 응답 (같은 이름·경로, 값 비움, 유효 기간 0) */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    /** 공통 속성: HttpOnly, Secure(설정), SameSite(설정), Path=/api/auth */
    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.isCookieSecure())
                .sameSite(props.getCookieSameSite())
                .path(PATH);
    }
}
