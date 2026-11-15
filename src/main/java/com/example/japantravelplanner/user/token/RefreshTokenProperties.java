package com.example.japantravelplanner.user.token;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 갱신 토큰 설정 (11-15 보안) */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.refresh")
public class RefreshTokenProperties {

    /** 갱신 토큰 유효 기간 (갱신할 때마다 다시 이만큼) */
    private int ttlDays = 7;
    /** 한 계정에 동시에 로그인할 수 있는 브라우저 수. 넘으면 가장 오래 안 쓴 브라우저를 로그아웃 (11-15 사용자 결정 5) */
    private int maxSessions = 5;
    /**
     * 바로 전에 바꾼 옛 토큰이 이 시간 안에 다시 오면 도둑이 아니라 같은 브라우저의 다른 탭으로 본다
     * (새 토큰 없이 접근 토큰만 준다). 지나서 오면 재사용으로 보고 그 브라우저의 토큰을 모두 폐기
     */
    private int reuseGraceSeconds = 10;
    /** 쿠키 Secure (HTTPS 에서만 보냄). 배포(HTTPS)에서는 반드시 true */
    private boolean cookieSecure = true;
    private String cookieSameSite = "Lax";
}
