package com.example.japantravelplanner.user.token;

import com.example.japantravelplanner.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 갱신 토큰 발급·회전·재사용 감지·로그아웃 (11-15 보안 2-1~2-4, 2-8).
 * - 로그인: 새 family(브라우저) 시작. 같은 브라우저의 예전 토큰이 같이 오면 그 family 는 닫는다(다시 세지 않게).
 *   살아 있는 브라우저가 maxSessions 개면 가장 오래 안 쓴 것부터 닫는다.
 * - 갱신: 옛 토큰 폐기(ROTATED) + 같은 family 에 새 토큰. 폐기된 토큰이 다시 오면(유예 시간 밖) 그 family 전부 폐기.
 * - 로그아웃: 그 family 전부 폐기.
 */
@Service
public class RefreshTokenService {

    private static final String RELOGIN_MESSAGE = "다시 로그인해주세요.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** 갱신 결과. newToken 이 null 이면 쿠키는 그대로 두고 접근 토큰만 새로 준다 (다른 탭이 방금 바꾼 경우) */
    public record Rotation(Long userId, String newToken) {
    }

    @Transactional
    public String startSession(Long userId, String previousToken) {
        Instant now = clock.instant();
        repository.deleteExpired(userId, now.minus(Duration.ofDays(props.getTtlDays())));
        if (previousToken != null && !previousToken.isBlank()) {
            repository.findByTokenHash(hash(previousToken))
                    .filter(t -> t.getUserId().equals(userId))
                    .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), now, RefreshToken.RELOGIN));
        }
        List<RefreshToken> active = repository.findActive(userId, now);   // 오래된 순
        for (int i = 0; i <= active.size() - props.getMaxSessions(); i++) {
            active.get(i).revoke(now, RefreshToken.EVICTED);
        }
        return issue(userId, UUID.randomUUID().toString(), now);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new UnauthorizedException(RELOGIN_MESSAGE);
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException(RELOGIN_MESSAGE));

        if (token.getRevokedAt() != null) {
            boolean justRotated = RefreshToken.ROTATED.equals(token.getRevokeReason())
                    && now.isBefore(token.getRevokedAt().plusSeconds(props.getReuseGraceSeconds()));
            if (justRotated && hasActive(token)) {
                return new Rotation(token.getUserId(), null);
            }
            // 이미 폐기된 토큰이 다시 왔다 → 도둑맞았을 수 있으니 그 브라우저의 토큰을 모두 폐기 (도둑·사용자 중 누가 먼저 썼든 들통)
            repository.revokeFamily(token.getFamilyId(), now, RefreshToken.REUSED);
            throw new UnauthorizedException(RELOGIN_MESSAGE);
        }
        if (!token.isActive(now)) throw new UnauthorizedException(RELOGIN_MESSAGE);

        token.revoke(now, RefreshToken.ROTATED);
        return new Rotation(token.getUserId(), issue(token.getUserId(), token.getFamilyId(), now));
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), clock.instant(), RefreshToken.LOGOUT));
    }

    public Duration ttl() {
        return Duration.ofDays(props.getTtlDays());
    }

    private boolean hasActive(RefreshToken token) {
        Instant now = clock.instant();
        return repository.findActive(token.getUserId(), now).stream()
                .anyMatch(t -> t.getFamilyId().equals(token.getFamilyId()));
    }

    private String issue(Long userId, String familyId, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(userId, hash(raw), familyId, now, now.plus(ttl())));
        return raw;
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
