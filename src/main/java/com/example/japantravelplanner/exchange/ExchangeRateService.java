package com.example.japantravelplanner.exchange;

import com.example.japantravelplanner.itinerary.meal.MealProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 일정 계산에 쓸 엔화 환율 (10-12, 이슈 #6).
 * 오늘(한국 시간) 받은 값이 있으면 그것 → 없으면 수출입은행(오늘부터 거슬러) → Frankfurter → 마지막 저장값 → 설정 고정값.
 * 환율 때문에 일정 생성이 막히면 안 되므로 예외를 던지지 않는다. 외부 호출이 있으니 트랜잭션 밖에서 부른다
 */
@Slf4j
@Service
public class ExchangeRateService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ExchangeRateClient client;
    private final ExchangeRateRepository repository;
    private final ExchangeRateProperties props;
    private final MealProperties mealProperties;
    private final Clock clock;
    private volatile Instant retryAt = Instant.EPOCH;

    /** @param clock 서버 시계 (ClockConfig). "오늘"은 한국 날짜로 본다 */
    public ExchangeRateService(ExchangeRateClient client, ExchangeRateRepository repository,
                               ExchangeRateProperties props, MealProperties mealProperties, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.props = props;
        this.mealProperties = mealProperties;
        this.clock = clock.withZone(KST);
    }

    public synchronized ExchangeRate current() {
        ExchangeRate fixed = new ExchangeRate(mealProperties.getKrwPerYen(), null, ExchangeRate.FIXED);
        if (!props.isEnabled()) return fixed;

        Optional<ExchangeRateRecord> latest = repository.findFirstByOrderByFetchedAtDesc();
        LocalDate today = LocalDate.now(clock);
        return latest
                .filter(r -> !r.getFetchedAt().toLocalDate().isBefore(today))   // 오늘 이미 받았다
                .map(ExchangeRateRecord::toRate)
                .orElseGet(() -> refresh(today, latest.map(ExchangeRateRecord::toRate).orElse(fixed)));
    }

    /** 오늘 값을 새로 받는다. 실패하면 마지막 값(없으면 고정값)을 쓰고 잠시 동안 다시 묻지 않는다 */
    private ExchangeRate refresh(LocalDate today, ExchangeRate fallback) {
        if (Instant.now(clock).isBefore(retryAt)) {
            return fallback;   // 조금 전에 실패했다
        }
        return fetch(today)
                .map(rate -> {
                    repository.save(ExchangeRateRecord.of(rate.krwPerYen(), rate.date(), rate.source(), LocalDateTime.now(clock)));
                    log.info("[Exchange] 환율 {}원/엔 ({} {})", rate.krwPerYen(), rate.date(), rate.source());
                    return rate;
                })
                .orElseGet(() -> {
                    retryAt = Instant.now(clock).plusSeconds(props.getRetryAfterMinutes() * 60L);
                    log.warn("[Exchange] 환율을 받지 못해 {} 값 사용 ({}원/엔)", fallback.source(), fallback.krwPerYen());
                    return fallback;
                });
    }

    private Optional<ExchangeRate> fetch(LocalDate today) {
        try {
            for (int i = 0; i <= props.getLookbackDays(); i++) {
                LocalDate date = today.minusDays(i);   // 그날 값이 비어 있으면(주말·11시 전) 전날로
                Optional<ExchangeRate> found = client.koreaexim(date).filter(this::sane)
                        .map(value -> new ExchangeRate(value, date, ExchangeRate.KOREAEXIM));
                if (found.isPresent()) return found;
            }
        } catch (ExchangeRateClient.Unavailable e) {
            log.info("[Exchange] 수출입은행 사용 불가 ({}), Frankfurter로", e.getMessage());
        }
        return client.frankfurter()
                .filter(d -> sane(d.krwPerYen()))
                .map(d -> new ExchangeRate(d.krwPerYen(), d.date(), ExchangeRate.FRANKFURTER));
    }

    private boolean sane(double krwPerYen) {
        boolean ok = krwPerYen >= props.getMinKrwPerYen() && krwPerYen <= props.getMaxKrwPerYen();
        if (!ok) log.warn("[Exchange] 범위를 벗어난 환율 {} 버림", krwPerYen);
        return ok;
    }
}
