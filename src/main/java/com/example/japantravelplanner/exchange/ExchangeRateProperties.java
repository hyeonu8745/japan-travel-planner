package com.example.japantravelplanner.exchange;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 엔화 환율 (10-12, 이슈 #6). 기본은 한국수출입은행 매매기준율, 실패하면 Frankfurter → 마지막 저장값 → 고정값 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.exchange-rate")
public class ExchangeRateProperties {

    private boolean enabled = true;
    /** 한국수출입은행 현재환율 API 인증키 (.env KOREAEXIM_API_KEY). 비어 있으면 Frankfurter부터 */
    private String koreaeximApiKey = "";
    private String koreaeximUrl = "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON";
    private String frankfurterUrl = "https://api.frankfurter.dev/v1/latest";
    private int timeoutSeconds = 5;
    /** 주말·공휴일·오전 11시 전에는 그날 값이 비어 있어서 며칠 전까지 거슬러 묻는다 */
    private int lookbackDays = 7;
    /** 두 곳 모두 실패하면 이 시간 동안 다시 묻지 않는다 (요청마다 외부 호출이 쌓이지 않게) */
    private int retryAfterMinutes = 30;
    /** 받은 값이 이 범위를 벗어나면 단위 오류로 보고 버린다 (원/1엔) */
    private double minKrwPerYen = 5.0;
    private double maxKrwPerYen = 15.0;
}
