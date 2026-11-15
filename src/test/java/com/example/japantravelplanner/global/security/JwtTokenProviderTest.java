package com.example.japantravelplanner.global.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-08 리팩터링: java.util.Date 대신 서버 시계(Clock), 비밀키 인코딩 UTF-8 명시 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-0123456789";
    private static final Instant NOW = Instant.parse("2026-11-08T08:00:00Z");

    private static JwtTokenProvider at(Instant now) {
        return new JwtTokenProvider(SECRET, 3_600_000, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 만든_토큰에서_사용자_id를_읽는다() {
        JwtTokenProvider provider = at(NOW);
        String token = provider.createToken(42L);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void 한_시간이_지나면_만료된다() {
        String token = at(NOW).createToken(42L);

        assertThat(at(NOW.plus(Duration.ofMinutes(59))).validateToken(token)).isTrue();
        assertThat(at(NOW.plus(Duration.ofMinutes(61))).validateToken(token)).isFalse();
    }

    @Test
    void 다른_키로_만든_토큰은_거절한다() {
        String token = new JwtTokenProvider(SECRET.replace('t', 'x'), 3_600_000, Clock.fixed(NOW, ZoneOffset.UTC))
                .createToken(1L);
        assertThat(at(NOW).validateToken(token)).isFalse();
        assertThat(at(NOW).validateToken("not-a-token")).isFalse();
    }

    @Test
    void 이메일은_넣지_않고_발급자와_종류를_넣는다() {
        String token = at(NOW).createToken(42L);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).contains("\"iss\":\"tabimate\"", "\"typ\":\"access\"").doesNotContain("email", "@");
    }

    @Test
    void 종류나_발급자가_다르면_접근_토큰으로_받지_않는다() {
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.util.Date exp = java.util.Date.from(NOW.plusSeconds(600));
        String noType = io.jsonwebtoken.Jwts.builder().issuer("tabimate").subject("42").expiration(exp).signWith(key).compact();
        String refreshType = io.jsonwebtoken.Jwts.builder().issuer("tabimate").subject("42").claim("typ", "refresh")
                .expiration(exp).signWith(key).compact();
        String otherIssuer = io.jsonwebtoken.Jwts.builder().issuer("someone").subject("42").claim("typ", "access")
                .expiration(exp).signWith(key).compact();

        assertThat(at(NOW).validateToken(noType)).isFalse();      // 11-15 이전에 만든 토큰도 여기 해당 → 한 번 다시 로그인
        assertThat(at(NOW).validateToken(refreshType)).isFalse();
        assertThat(at(NOW).validateToken(otherIssuer)).isFalse();
    }
}
