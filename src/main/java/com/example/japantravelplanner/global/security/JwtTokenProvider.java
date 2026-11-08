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

    public String createToken(Long userId, String email) {
        Instant now = clock.instant();
        // 토큰 라이브러리(jjwt)가 java.util.Date 를 받아서 경계에서만 바꾼다
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parser().parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            parser().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 만료 확인도 같은 서버 시계로 (테스트에서 시간을 고정할 수 있게) */
    private JwtParser parser() {
        return Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build();
    }
}
