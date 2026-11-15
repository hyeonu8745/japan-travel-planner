package com.example.japantravelplanner.user.security;

import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 무차별 대입 막기 (11-15 보안 1-1).
 * 같은 이메일 + 같은 IP 로 연속 maxFailures 번 틀리면 lockMinutes 분 동안 맞는 비밀번호도 받지 않는다.
 * 마지막 실패에서 lockMinutes 가 지나면 횟수를 처음부터 센다. 성공하면 바로 지운다.
 * 서버 한 대라 메모리에 둔다 (서버를 여러 대로 늘리면 Redis 로 옮긴다, GenerationLock 과 같은 한계).
 * 막힌 동안에도 "이메일이 없다 / 비밀번호가 틀렸다"를 구분해 알려 주지 않는다.
 */
@Component
public class LoginAttemptLimiter {

    /** 오래된 기록 정리를 시작하는 개수 (주소를 바꿔 가며 계속 틀려도 메모리가 끝없이 늘지 않게) */
    static final int CLEANUP_THRESHOLD = 10_000;

    private final LoginAttemptProperties props;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(LoginAttemptProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** 막힌 상태면 429 */
    public void checkAllowed(String email, String ip) {
        Attempt a = attempts.get(key(email, ip));
        if (a == null) return;
        Instant now = clock.instant();
        if (expired(a, now)) {
            attempts.remove(key(email, ip), a);
            return;
        }
        if (a.failures() >= props.getMaxFailures()) {
            long minutes = Math.max(1, (Duration.between(now, a.lastFailure().plus(lockDuration())).toSeconds() + 59) / 60);
            throw new TooManyRequestsException("로그인 시도가 너무 많아요. " + minutes + "분 뒤에 다시 시도해주세요.");
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (attempts.size() >= CLEANUP_THRESHOLD) {
            attempts.values().removeIf(a -> expired(a, now));
        }
        attempts.compute(key(email, ip), (k, a) ->
                a == null || expired(a, now) ? new Attempt(1, now) : new Attempt(a.failures() + 1, now));
    }

    public void recordSuccess(String email, String ip) {
        attempts.remove(key(email, ip));
    }

    int size() {
        return attempts.size();
    }

    private boolean expired(Attempt a, Instant now) {
        return !now.isBefore(a.lastFailure().plus(lockDuration()));
    }

    private Duration lockDuration() {
        return Duration.ofMinutes(props.getLockMinutes());
    }

    private static String key(String email, String ip) {
        return email + "|" + (ip == null ? "" : ip);
    }

    private record Attempt(int failures, Instant lastFailure) {
    }
}
