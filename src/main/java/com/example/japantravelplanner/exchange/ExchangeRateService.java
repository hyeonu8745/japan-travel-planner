package com.example.japantravelplanner.exchange;

import com.example.japantravelplanner.itinerary.meal.MealProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired   // 생성자가 두 개(테스트용 시계)라 Spring이 쓸 생성자를 지정한다 (10-11 사고 사례)
    public ExchangeRateService(ExchangeRateClient client, ExchangeRateRepository repository,
                               ExchangeRateProperties props, MealProperties mealProperties) {
        this(client, repository, props, mealProperties, Clock.system(KST));
    }

    ExchangeRateService(ExchangeRateClient client, ExchangeRateRepository repository,
                        ExchangeRateProperties props, MealProperties mealProperties, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.props = props;
        this.mealProperties = mealProperties;
        this.clock = clock;
    }

    public synchronized ExchangeRate current() {
        ExchangeRate fixed = new ExchangeRate(mealProperties.getKrwPerYen(), null, ExchangeRate.FIXED);
        if (!props.isEnabled()) return fixed;

        Optional<ExchangeRateRecord> latest = repository.findFirstByOrderByFetchedAtDesc();
        LocalDate today = LocalDate.now(clock);
        if (latest.isPresent() && !latest.get().getFetchedAt().toLocalDate().isBefore(today)) {
            return latest.get().toRate();   // 오늘 이미 받았다
        }
        if (Instant.now(clock).isBefore(retryAt)) {
            return latest.map(ExchangeRateRecord::toRate).orElse(fixed);   // 조금 전에 실패했다
        }

        Optional<ExchangeRate> fetched = fetch(today);
        if (fetched.isPresent()) {
            ExchangeRate rate = fetched.get();
            repository.save(ExchangeRateRecord.of(rate.krwPerYen(), rate.date(), rate.source(), LocalDateTime.now(clock)));
            log.info("[Exchange] 환율 {}원/엔 ({} {})", rate.krwPerYen(), rate.date(), rate.source());
            return rate;
        }
        retryAt = Instant.now(clock).plusSeconds(props.getRetryAfterMinutes() * 60L);
        ExchangeRate fallback = latest.map(ExchangeRateRecord::toRate).orElse(fixed);
        log.warn("[Exchange] 환율을 받지 못해 {} 값 사용 ({}원/엔)", fallback.source(), fallback.krwPerYen());
        return fallback;
    }

    private Optional<ExchangeRate> fetch(LocalDate today) {
        try {
            for (int i = 0; i <= props.getLookbackDays(); i++) {
                LocalDate date = today.minusDays(i);   // 그날 값이 비어 있으면(주말·11시 전) 전날로
                Optional<Double> value = client.koreaexim(date).filter(this::sane);
                if (value.isPresent()) return Optional.of(new ExchangeRate(value.get(), date, ExchangeRate.KOREAEXIM));
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
