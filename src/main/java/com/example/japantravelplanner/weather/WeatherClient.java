package com.example.japantravelplanner.weather;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Open-Meteo 호출 (10-16). 실패하면 예외 대신 빈 값 (날씨는 일정에 꼭 필요한 정보가 아니다, 원칙 6).
 * 시간대는 Asia/Tokyo (일본 날짜 기준 하루).
 */
@Slf4j
@Component
public class WeatherClient {

    private static final String FORECAST_DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum";
    private static final String ARCHIVE_DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum";

    private final WeatherProperties props;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public WeatherClient(WeatherProperties props, JsonMapper jsonMapper) {
        this.props = props;
        this.jsonMapper = jsonMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public Optional<List<DailyWeather>> forecast(double lat, double lng, LocalDate start, LocalDate end) {
        return call(props.getForecastUrl(), FORECAST_DAILY, lat, lng, start, end, "예보");
    }

    public Optional<List<DailyWeather>> archive(double lat, double lng, LocalDate start, LocalDate end) {
        return call(props.getArchiveUrl(), ARCHIVE_DAILY, lat, lng, start, end, "작년 날씨");
    }

    private Optional<List<DailyWeather>> call(String url, String daily, double lat, double lng,
                                              LocalDate start, LocalDate end, String label) {
        try {
            String body = restClient.get()
                    .uri(url + "?latitude={lat}&longitude={lng}&daily={daily}&timezone=Asia%2FTokyo&start_date={s}&end_date={e}",
                            round(lat), round(lng), daily, start, end)
                    .retrieve().body(String.class);
            return Optional.of(parse(jsonMapper, body));
        } catch (RuntimeException e) {
            log.warn("[Weather] {} 조회 실패 {}~{}: {}", label, start, end, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 좌표는 소수 둘째 자리까지 (약 1km, 캐시가 잘 맞게) */
    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** {"daily":{"time":["2026-10-25",...],"weather_code":[61,...],...}} → 하루씩 */
    static List<DailyWeather> parse(JsonMapper mapper, String body) {
        JsonNode daily = mapper.readTree(body).path("daily");
        JsonNode time = daily.path("time");
        List<DailyWeather> result = new ArrayList<>();
        for (int i = 0; i < time.size(); i++) {
            result.add(new DailyWeather(LocalDate.parse(time.get(i).asString()),
                    intAt(daily, "weather_code", i), doubleAt(daily, "temperature_2m_max", i),
                    doubleAt(daily, "temperature_2m_min", i), intAt(daily, "precipitation_probability_max", i),
                    doubleAt(daily, "precipitation_sum", i)));
        }
        return result;
    }

    private static Integer intAt(JsonNode daily, String field, int i) {
        JsonNode v = daily.path(field).path(i);
        return v.isNumber() ? v.asInt() : null;
    }

    private static Double doubleAt(JsonNode daily, String field, int i) {
        JsonNode v = daily.path(field).path(i);
        return v.isNumber() ? v.asDouble() : null;
    }
}
