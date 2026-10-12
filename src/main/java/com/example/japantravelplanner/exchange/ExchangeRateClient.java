package com.example.japantravelplanner.exchange;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 외부 환율 API 두 곳. 실패하면 예외 대신 빈 값 (일정 생성을 막지 않는다).
 * 주의: 수출입은행 요청 주소에 인증키가 들어가므로 URL을 로그에 남기지 않는다
 */
@Slf4j
@Component
public class ExchangeRateClient {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExchangeRateProperties props;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public ExchangeRateClient(ExchangeRateProperties props, JsonMapper jsonMapper) {
        this.props = props;
        this.jsonMapper = jsonMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 한국수출입은행 그날 매매기준율 (1엔당 원). 주말·공휴일·11시 전이면 빈 값.
     * 연결 실패·인증키 오류·한도 초과는 Unavailable (며칠을 거슬러 다시 묻지 않게)
     */
    public Optional<Double> koreaexim(LocalDate date) {
        if (props.getKoreaeximApiKey() == null || props.getKoreaeximApiKey().isBlank()) {
            throw new Unavailable("인증키 없음");
        }
        try {
            String body = restClient.get()
                    .uri(props.getKoreaeximUrl() + "?authkey={key}&searchdate={date}&data=AP01",
                            props.getKoreaeximApiKey(), date.format(YYYYMMDD))
                    .retrieve().body(String.class);
            return parseKoreaexim(jsonMapper, body, date);
        } catch (Unavailable e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[Exchange] 수출입은행 환율 조회 실패 {}: {}", date, e.getClass().getSimpleName());
            throw new Unavailable(e.getClass().getSimpleName());
        }
    }

    /** 수출입은행을 지금 쓸 수 없음 (다음 수단으로 넘어간다) */
    public static class Unavailable extends RuntimeException {
        public Unavailable(String reason) {
            super(reason);
        }
    }

    /** Frankfurter 최신 JPY→KRW (값, 기준일) */
    public Optional<Dated> frankfurter() {
        try {
            String body = restClient.get()
                    .uri(props.getFrankfurterUrl() + "?base=JPY&symbols=KRW")
                    .retrieve().body(String.class);
            return parseFrankfurter(jsonMapper, body);
        } catch (RuntimeException e) {
            log.warn("[Exchange] Frankfurter 환율 조회 실패: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public record Dated(double krwPerYen, LocalDate date) {
    }

    /**
     * 수출입은행 응답: [{"result":1,"cur_unit":"JPY(100)","deal_bas_r":"951.05",...}, ...]. 엔화는 100엔 단위.
     * 비영업일·11시 전은 빈 배열(또는 null). result 3은 인증키 오류, 4는 하루 호출 한도 초과
     */
    static Optional<Double> parseKoreaexim(JsonMapper mapper, String body, LocalDate date) {
        if (body == null || body.isBlank()) return Optional.empty();
        JsonNode root = mapper.readTree(body);
        if (!root.isArray()) return Optional.empty();
        for (JsonNode row : root) {
            int result = row.path("result").asInt(1);
            if (result != 1) {
                log.warn("[Exchange] 수출입은행 응답 result={} ({})", result, date);
                throw new Unavailable("result=" + result);
            }
            String unit = row.path("cur_unit").asString("");
            if (!unit.startsWith("JPY")) continue;
            String raw = row.path("deal_bas_r").asString("").replace(",", "").trim();
            if (raw.isEmpty()) return Optional.empty();
            double value = Double.parseDouble(raw);
            return Optional.of(unit.contains("(100)") ? value / 100.0 : value);
        }
        return Optional.empty();
    }

    /** Frankfurter 응답: {"amount":1.0,"base":"JPY","date":"2026-10-09","rates":{"KRW":9.51}} */
    static Optional<Dated> parseFrankfurter(JsonMapper mapper, String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        JsonNode root = mapper.readTree(body);
        JsonNode krw = root.path("rates").path("KRW");
        if (!krw.isNumber()) return Optional.empty();
        double amount = root.path("amount").asDouble(1.0);
        LocalDate date = LocalDate.parse(root.path("date").asString(LocalDate.now().toString()));
        return Optional.of(new Dated(krw.asDouble() / (amount == 0 ? 1.0 : amount), date));
    }
}
