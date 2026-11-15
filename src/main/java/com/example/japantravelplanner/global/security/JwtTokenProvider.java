package com.example.japantravelplanner.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * 접근 토큰(JWT, HS256) 발급·검증. 1시간, 내용은 사용자 id·발급자(tabimate)·종류(access)·발급/만료 시각만 (이메일 없음).
 * 만료 확인도 서버 시계(Clock)로 해서 테스트에서 시간을 고정할 수 있다. 갱신 토큰은 JWT 가 아니다 (user.token).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock;

    /**
     * @param secret 서명 비밀키 (.env 의 JWT_SECRET, 32바이트 이상)
     * @param expirationMs 접근 토큰 유효 시간 (기본 1시간)
     * @param clock 서버 시계
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            Clock clock
    ) {
        // 문자 인코딩을 UTF-8 로 명시 (getBytes() 만 쓰면 OS 기본값을 따라 PC 마다 키가 달라질 수 있다)
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    /** 발급자·종류를 넣고 검사한다 (갱신 토큰 등 다른 토큰을 접근 토큰처럼 쓰지 못하게) */
    static final String ISSUER = "tabimate";
    static final String TYPE_CLAIM = "typ";
    static final String ACCESS = "access";

    /**
     * 접근 토큰. 이메일은 넣지 않는다 (JWT 내용은 누구나 열어 볼 수 있다).
     * 사용자 id(subject), 발급자, 종류(access), 발급·만료 시각만
     */
    public String createToken(Long userId) {
        Instant now = clock.instant();
        // 토큰 라이브러리(jjwt)가 java.util.Date 를 받아서 경계에서만 바꾼다
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim(TYPE_CLAIM, ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /** 접근 토큰에서 사용자 id (검증을 통과한 토큰만 넘긴다) */
    public Long getUserIdFromToken(String token) {
        return Long.parseLong(accessClaims(token).getSubject());
    }

    /** 서명·만료·발급자·종류(access)가 모두 맞는지 */
    public boolean validateToken(String token) {
        try {
            accessClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 서명·만료·발급자 확인 + 종류가 access 인지 */
    private Claims accessClaims(String token) {
        Claims claims = parser().parseSignedClaims(token).getPayload();
        if (!ACCESS.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new io.jsonwebtoken.JwtException("접근 토큰이 아님");
        }
        return claims;
    }

    /** 만료 확인도 같은 서버 시계로 (테스트에서 시간을 고정할 수 있게) */
    private JwtParser parser() {
        return Jwts.parser().verifyWith(key).requireIssuer(ISSUER).clock(() -> Date.from(clock.instant())).build();
    }
}
