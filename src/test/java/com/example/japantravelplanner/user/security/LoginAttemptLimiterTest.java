package com.example.japantravelplanner.user.security;

import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-15T07:00:00Z");

        void plus(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(new LoginAttemptProperties(), clock);

    private void fail(int times, String email, String ip) {
        for (int i = 0; i < times; i++) limiter.recordFailure(email, ip);
    }

    @Test
    void 다섯_번_틀리면_막히고_남은_분을_알려준다() {
        fail(4, "a@x.com", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();

        fail(1, "a@x.com", "1.1.1.1");
        assertThatThrownBy(() -> limiter.checkAllowed("a@x.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("로그인 시도가 너무 많아요. 15분 뒤에 다시 시도해주세요.");

        clock.plus(Duration.ofMinutes(10).plusSeconds(30));
        assertThatThrownBy(() -> limiter.checkAllowed("a@x.com", "1.1.1.1"))
                .hasMessage("로그인 시도가 너무 많아요. 5분 뒤에 다시 시도해주세요.");
    }

    @Test
    void 십오_분이_지나면_풀리고_횟수도_처음부터_센다() {
        fail(5, "a@x.com", "1.1.1.1");
        clock.plus(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();

        fail(4, "a@x.com", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void 성공하면_횟수가_지워진다() {
        fail(4, "a@x.com", "1.1.1.1");
        limiter.recordSuccess("a@x.com", "1.1.1.1");
        fail(4, "a@x.com", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void 다른_이메일이나_다른_주소는_따로_센다() {
        fail(5, "a@x.com", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("b@x.com", "1.1.1.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed("a@x.com", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void 기록이_많이_쌓이면_지난_기록부터_정리한다() {
        for (int i = 0; i < LoginAttemptLimiter.CLEANUP_THRESHOLD; i++) limiter.recordFailure("u" + i + "@x.com", "1.1.1.1");
        clock.plus(Duration.ofMinutes(16));
        limiter.recordFailure("new@x.com", "1.1.1.1");
        assertThat(limiter.size()).isEqualTo(1);
    }
}
