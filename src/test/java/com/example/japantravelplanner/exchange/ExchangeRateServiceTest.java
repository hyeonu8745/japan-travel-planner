package com.example.japantravelplanner.exchange;

import com.example.japantravelplanner.itinerary.meal.MealProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 엔화 환율 고르기 (10-12, 이슈 #6) */
class ExchangeRateServiceTest {

    // 2026-10-12(월) 10:00 한국 시간 — 수출입은행은 11시 전이라 오늘 값이 비어 있다
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 12, 10, 0, 0, 0, ExchangeRateService.KST);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final ExchangeRateClient client = mock(ExchangeRateClient.class);
    private final ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
    private final ExchangeRateProperties props = new ExchangeRateProperties();
    private Clock clock = Clock.fixed(NOW.toInstant(), ExchangeRateService.KST);
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        props.setKoreaeximApiKey("test-key");
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(client.koreaexim(any())).thenReturn(Optional.empty());
        when(client.frankfurter()).thenReturn(Optional.empty());
        service = new ExchangeRateService(client, repository, props, new MealProperties(), clock);
    }

    @Test
    void 오늘_값이_비어_있으면_전_영업일로_거슬러_묻고_저장한다() {
        // 월요일 11시 전 → 오늘 없음, 일·토 없음 → 금요일(10-09) 값
        when(client.koreaexim(LocalDate.of(2026, 10, 9))).thenReturn(Optional.of(9.5105));

        ExchangeRate rate = service.current();

        assertThat(rate).isEqualTo(new ExchangeRate(9.5105, LocalDate.of(2026, 10, 9), ExchangeRate.KOREAEXIM));
        verify(client).koreaexim(TODAY);
        verify(client).koreaexim(TODAY.minusDays(1));
        verify(repository).save(any());
        verify(client, never()).frankfurter();
    }

    @Test
    void 오늘_이미_받은_값이_있으면_외부를_부르지_않는다() {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.of(
                ExchangeRateRecord.of(9.4, LocalDate.of(2026, 10, 9), ExchangeRate.KOREAEXIM, NOW.toLocalDateTime().minusHours(1))));

        assertThat(service.current().krwPerYen()).isEqualTo(9.4);
        verifyNoInteractions(client);
    }

    @Test
    void 수출입은행을_쓸_수_없으면_며칠을_묻지_않고_Frankfurter로() {
        when(client.koreaexim(any())).thenThrow(new ExchangeRateClient.Unavailable("result=3"));
        when(client.frankfurter()).thenReturn(Optional.of(new ExchangeRateClient.Dated(9.47, LocalDate.of(2026, 10, 9))));

        ExchangeRate rate = service.current();

        assertThat(rate.source()).isEqualTo(ExchangeRate.FRANKFURTER);
        assertThat(rate.krwPerYen()).isEqualTo(9.47);
        verify(client, times(1)).koreaexim(any());
    }

    @Test
    void 모두_실패하면_마지막_저장값_그것도_없으면_고정값이고_잠시_다시_묻지_않는다() {
        ExchangeRate fixed = service.current();
        assertThat(fixed).isEqualTo(new ExchangeRate(9.0, null, ExchangeRate.FIXED));

        service.current();   // 30분 안에는 외부를 다시 부르지 않는다
        verify(client, times(1)).frankfurter();

        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.of(
                ExchangeRateRecord.of(9.3, LocalDate.of(2026, 10, 8), ExchangeRate.KOREAEXIM, LocalDateTime.of(2026, 10, 8, 12, 0))));
        assertThat(service.current().krwPerYen()).isEqualTo(9.3);
    }

    @Test
    void 범위를_벗어난_값은_단위_오류로_보고_버린다() {
        // 100엔당 값을 그대로 받은 경우 (951.05)
        when(client.koreaexim(any())).thenReturn(Optional.of(951.05));
        when(client.frankfurter()).thenReturn(Optional.of(new ExchangeRateClient.Dated(9.47, TODAY)));

        assertThat(service.current().source()).isEqualTo(ExchangeRate.FRANKFURTER);
    }

    @Test
    void 인증키가_없으면_Frankfurter부터_꺼져_있으면_고정값() {
        props.setKoreaeximApiKey("");
        when(client.koreaexim(any())).thenThrow(new ExchangeRateClient.Unavailable("인증키 없음"));
        when(client.frankfurter()).thenReturn(Optional.of(new ExchangeRateClient.Dated(9.47, TODAY)));
        assertThat(service.current().source()).isEqualTo(ExchangeRate.FRANKFURTER);

        props.setEnabled(false);
        ExchangeRateService off = new ExchangeRateService(client, repository, props, new MealProperties(), clock);
        assertThat(off.current().source()).isEqualTo(ExchangeRate.FIXED);
    }
}
