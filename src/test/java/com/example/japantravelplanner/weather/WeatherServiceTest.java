package com.example.japantravelplanner.weather;

import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 여행 날씨 (10-16): 16일 안은 예보, 그 밖은 작년 같은 날, 실패는 NONE */
class WeatherServiceTest {

    private final WeatherClient client = mock(WeatherClient.class);
    private final WeatherProperties props = new WeatherProperties();
    // 오늘 = 2026-10-16 (일본)
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T03:00:00Z"), WeatherService.JST);
    private final WeatherService service = new WeatherService(mock(TripRepository.class), client, props, clock);

    private static DailyWeather day(String date, Integer code, Integer prob, Double mm) {
        return new DailyWeather(LocalDate.parse(date), code, 21.0, 14.0, prob, mm);
    }

    @Test
    void 열흘_뒤_여행은_예보로_비_확률이_높은_날에_비_오는_날_일정을_권한다() {
        when(client.forecast(anyDouble(), anyDouble(), eq(LocalDate.of(2026, 10, 25)), eq(LocalDate.of(2026, 10, 27))))
                .thenReturn(Optional.of(List.of(day("2026-10-25", 1, 10, 0.0), day("2026-10-26", 61, 80, 12.5),
                        day("2026-10-27", 3, 40, 0.4))));

        WeatherResponseDto dto = service.build(9L, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 27), 35.69, 139.70);

        assertThat(dto.source()).isEqualTo("FORECAST");
        assertThat(dto.days()).extracting(WeatherResponseDto.DayDto::label).containsExactly("구름 조금", "비", "흐림");
        assertThat(dto.days().get(1).rainy()).isTrue();
        assertThat(dto.days().get(1).suggestRainPlan()).isTrue();
        assertThat(dto.days().get(1).rainProbability()).isEqualTo(80);
        assertThat(dto.days().get(0).suggestRainPlan()).isFalse();
        assertThat(dto.attribution()).contains("Open-Meteo");
        verify(client, never()).archive(anyDouble(), anyDouble(), any(), any());
    }

    @Test
    void 한_달_뒤_여행은_작년_같은_날_날씨로_보여주고_일정_권유는_하지_않는다() {
        when(client.archive(anyDouble(), anyDouble(), eq(LocalDate.of(2025, 11, 20)), eq(LocalDate.of(2025, 11, 21))))
                .thenReturn(Optional.of(List.of(day("2025-11-20", 63, null, 18.0), day("2025-11-21", 0, null, 0.0))));

        WeatherResponseDto dto = service.build(9L, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21), 35.69, 139.70);

        assertThat(dto.source()).isEqualTo("LAST_YEAR");
        assertThat(dto.message()).contains("작년 같은 날").contains("16일 전부터");
        assertThat(dto.days().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(dto.days().get(0).observedDate()).isEqualTo(LocalDate.of(2025, 11, 20));
        assertThat(dto.days().get(0).rainy()).isTrue();
        assertThat(dto.days().get(0).suggestRainPlan()).isFalse();   // 작년 날씨로는 권하지 않는다
        assertThat(dto.days().get(0).rainProbability()).isNull();
        assertThat(dto.days().get(1).condition()).isEqualTo("SUNNY");
    }

    @Test
    void 예보_범위를_걸치면_앞은_예보_뒤는_작년이고_같은_요청은_캐시한다() {
        when(client.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(Optional.of(List.of(day("2026-10-31", 0, 0, 0.0))));
        when(client.archive(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(Optional.of(List.of(day("2025-11-01", 3, null, 0.0))));

        WeatherResponseDto dto = service.build(9L, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), 35.69, 139.70);
        service.build(9L, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), 35.69, 139.70);

        assertThat(dto.source()).isEqualTo("MIXED");
        assertThat(dto.days()).extracting(WeatherResponseDto.DayDto::source).containsExactly("FORECAST", "LAST_YEAR");
        verify(client, times(1)).forecast(anyDouble(), anyDouble(), any(), any());
        verify(client, times(1)).archive(anyDouble(), anyDouble(), any(), any());
    }

    @Test
    void 받지_못하면_NONE으로_통과하고_다음에_다시_묻는다() {
        when(client.forecast(anyDouble(), anyDouble(), any(), any())).thenReturn(Optional.empty());

        WeatherResponseDto dto = service.build(9L, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), 35.69, 139.70);
        service.build(9L, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), 35.69, 139.70);

        assertThat(dto.source()).isEqualTo("NONE");
        assertThat(dto.days().get(0).condition()).isEqualTo("UNKNOWN");
        verify(client, times(2)).forecast(anyDouble(), anyDouble(), any(), any());
    }

    @Test
    void 응답_해석과_날씨_코드() {
        String body = """
                {"daily":{"time":["2026-10-25","2026-10-26"],"weather_code":[95,null],
                 "temperature_2m_max":[22.1,20.4],"temperature_2m_min":[15.0,13.2],
                 "precipitation_probability_max":[90,null],"precipitation_sum":[25.3,0.0]}}
                """;
        List<DailyWeather> days = WeatherClient.parse(JsonMapper.builder().build(), body);

        assertThat(days).hasSize(2);
        assertThat(days.get(0).weatherCode()).isEqualTo(95);
        assertThat(days.get(0).rainProbability()).isEqualTo(90);
        assertThat(days.get(1).weatherCode()).isNull();
        assertThat(WeatherCondition.of(95)).isEqualTo(WeatherCondition.THUNDER);
        assertThat(WeatherCondition.of(81).label()).isEqualTo("비");
        assertThat(WeatherCondition.of(73)).isEqualTo(WeatherCondition.SNOW);
        assertThat(WeatherClient.round(35.68123)).isEqualTo(35.68);
    }
}
