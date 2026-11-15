package com.example.japantravelplanner.user.token;

import com.example.japantravelplanner.global.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 11-15 보안: 갱신 토큰 회전·재사용 감지·브라우저 5개 제한·로그아웃 */
class RefreshTokenServiceTest {

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
    private final List<RefreshToken> rows = new ArrayList<>();
    private final RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(repo, new RefreshTokenProperties(), clock);

    /** 메모리에 저장하는 가짜 저장소 */
    @BeforeEach
    void fakeRepository() {
        when(repo.save(any())).thenAnswer(inv -> {
            rows.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repo.findByTokenHash(anyString())).thenAnswer(inv ->
                rows.stream().filter(t -> t.getTokenHash().equals(inv.getArgument(0))).findFirst());
        when(repo.findActive(anyLong(), any())).thenAnswer(inv -> rows.stream()
                .filter(t -> t.getUserId().equals(inv.getArgument(0)) && t.isActive(inv.getArgument(1)))
                .sorted(Comparator.comparing(RefreshToken::getCreatedAt)).toList());
        when(repo.revokeFamily(anyString(), any(), anyString())).thenAnswer(inv -> {
            rows.stream().filter(t -> t.getFamilyId().equals(inv.getArgument(0)))
                    .forEach(t -> t.revoke(inv.getArgument(1), inv.getArgument(2)));
            return 1;
        });
    }

    private RefreshToken row(String raw) {
        return rows.stream().filter(t -> t.getTokenHash().equals(RefreshTokenService.hash(raw))).findFirst().orElseThrow();
    }

    private long activeBrowsers(long userId) {
        return rows.stream().filter(t -> t.getUserId() == userId && t.isActive(clock.now)).count();
    }

    @Test
    void 토큰_원문은_저장하지_않고_해시만_저장한다() {
        String raw = service.startSession(1L, null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTokenHash()).hasSize(64).isNotEqualTo(raw);
        assertThat(rows.get(0).getExpiresAt()).isEqualTo(clock.now.plus(Duration.ofDays(7)));
    }

    @Test
    void 갱신하면_새_토큰을_주고_옛_토큰은_폐기한다() {
        String first = service.startSession(1L, null);
        clock.plus(Duration.ofHours(1));

        RefreshTokenService.Rotation r = service.refresh(first);

        assertThat(r.userId()).isEqualTo(1L);
        assertThat(r.newToken()).isNotNull().isNotEqualTo(first);
        assertThat(row(first).getRevokeReason()).isEqualTo(RefreshToken.ROTATED);
        assertThat(row(r.newToken()).getFamilyId()).isEqualTo(row(first).getFamilyId());
        assertThat(row(r.newToken()).getExpiresAt()).isEqualTo(clock.now.plus(Duration.ofDays(7)));
    }

    @Test
    void 바꾼_지_10초_안에_옛_토큰이_다시_오면_다른_탭으로_보고_접근_토큰만_준다() {
        String first = service.startSession(1L, null);
        String second = service.refresh(first).newToken();
        clock.plus(Duration.ofSeconds(5));

        RefreshTokenService.Rotation r = service.refresh(first);

        assertThat(r.userId()).isEqualTo(1L);
        assertThat(r.newToken()).isNull();
        assertThat(row(second).isActive(clock.now)).isTrue();
    }

    @Test
    void 폐기된_토큰이_나중에_다시_오면_그_브라우저의_토큰을_모두_폐기한다() {
        String first = service.startSession(1L, null);
        String second = service.refresh(first).newToken();
        String otherBrowser = service.startSession(1L, null);
        clock.plus(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.refresh(first)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("다시 로그인해주세요.");
        assertThat(row(second).getRevokeReason()).isEqualTo(RefreshToken.REUSED);
        assertThatThrownBy(() -> service.refresh(second)).isInstanceOf(UnauthorizedException.class);
        // 다른 브라우저는 그대로
        assertThat(row(otherBrowser).isActive(clock.now)).isTrue();
    }

    @Test
    void 없거나_만료된_토큰은_401() {
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.refresh("nope")).isInstanceOf(UnauthorizedException.class);
        String raw = service.startSession(1L, null);
        clock.plus(Duration.ofDays(7));
        assertThatThrownBy(() -> service.refresh(raw)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 브라우저는_5개까지이고_6번째_로그인은_가장_오래된_브라우저를_닫는다() {
        List<String> browsers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            browsers.add(service.startSession(1L, null));
            clock.plus(Duration.ofMinutes(1));
        }
        service.refresh(browsers.get(0));          // 첫 번째 브라우저는 방금 썼다 → 가장 오래 안 쓴 건 두 번째
        clock.plus(Duration.ofMinutes(1));
        service.startSession(2L, null);             // 다른 사용자는 상관없음

        service.startSession(1L, null);

        assertThat(activeBrowsers(1L)).isEqualTo(5);
        assertThat(row(browsers.get(1)).getRevokeReason()).isEqualTo(RefreshToken.EVICTED);
        assertThat(activeBrowsers(2L)).isEqualTo(1);
    }

    @Test
    void 같은_브라우저에서_다시_로그인하면_예전_로그인을_닫고_새로_센다() {
        String before = service.startSession(1L, null);
        service.startSession(1L, before);

        assertThat(row(before).getRevokeReason()).isEqualTo(RefreshToken.RELOGIN);
        assertThat(activeBrowsers(1L)).isEqualTo(1);
    }

    @Test
    void 다른_사람의_예전_쿠키로는_그_사람_로그인을_닫지_않는다() {
        String someoneElse = service.startSession(2L, null);
        service.startSession(1L, someoneElse);
        assertThat(row(someoneElse).isActive(clock.now)).isTrue();
    }

    @Test
    void 로그아웃하면_그_브라우저만_폐기되고_다시_갱신할_수_없다() {
        String mine = service.startSession(1L, null);
        String other = service.startSession(1L, null);

        service.logout(mine);
        service.logout(null);
        service.logout("unknown");

        assertThat(row(mine).getRevokeReason()).isEqualTo(RefreshToken.LOGOUT);
        assertThat(row(other).isActive(clock.now)).isTrue();
        assertThatThrownBy(() -> service.refresh(mine)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 쿠키는_HttpOnly_이고_auth_경로에만_보낸다() {
        RefreshTokenProperties props = new RefreshTokenProperties();
        var cookie = new RefreshCookie(props).issue("abc");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(new RefreshCookie(props).clear().getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(Optional.of(cookie.getName())).contains("refresh_token");
    }
}
