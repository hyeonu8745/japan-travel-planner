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

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            Clock clock
    ) {
        // 11-08: 문자 인코딩을 명시 (예전 getBytes() 는 OS 기본값을 따랐다)
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    /** 11-15 보안: 발급자·종류를 넣고 검사한다 (갱신 토큰 등 다른 토큰을 접근 토큰처럼 쓰지 못하게) */
    static final String ISSUER = "tabimate";
    static final String TYPE_CLAIM = "typ";
    static final String ACCESS = "access";

    /**
     * 접근 토큰. 11-15 보안: 이메일을 넣지 않는다 (JWT 내용은 누구나 열어 볼 수 있다).
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

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(accessClaims(token).getSubject());
    }

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
