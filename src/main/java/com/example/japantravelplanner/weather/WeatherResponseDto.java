package com.example.japantravelplanner.weather;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행 날짜별 날씨 (10-16, 화면 머리의 날씨 칸).
 *
 * @param source      FORECAST(모두 예보) / LAST_YEAR(모두 작년 같은 날) / MIXED / NONE(받지 못함)
 * @param message     화면에 그대로 보여줄 한 줄 (예: "출발 16일 전부터 실제 예보로 바뀌어요")
 * @param attribution 출처 표시 (Open-Meteo 이용 조건)
 */
public record WeatherResponseDto(Long tripId, String source, String message, List<DayDto> days, String attribution) {

    /**
     * @param source          FORECAST / LAST_YEAR / NONE
     * @param observedDate    작년 날씨면 실제 날짜 (예보면 date와 같음)
     * @param weatherCode     WMO 날씨 코드 (null이면 모름)
     * @param condition       SUNNY, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER, UNKNOWN (아이콘용)
     * @param label           한국어 ("맑음", "비" …)
     * @param rainProbability 강수 확률 % (예보만, 작년 날씨는 null)
     * @param rainy           비 오는 날로 볼지 (예보: 강수 확률 60% 이상 또는 비 코드 / 작년: 강수량 5mm 이상 또는 비 코드)
     * @param suggestRainPlan 예보로 비가 예상될 때만 true → "비 오는 날 일정 보기" 버튼
     */
    public record DayDto(int dayNumber, LocalDate date, String source, LocalDate observedDate, Integer weatherCode,
                         String condition, String label, Double tempMax, Double tempMin, Integer rainProbability,
                         Double rainMm, boolean rainy, boolean suggestRainPlan) {
    }

    public static final String ATTRIBUTION = "Weather data by Open-Meteo.com";
}
