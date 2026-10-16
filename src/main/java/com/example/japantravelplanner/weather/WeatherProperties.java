package com.example.japantravelplanner.weather;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 여행 날씨 (10-16). Open-Meteo (키 없음, 비상업 무료, 출처 표시 "Weather data by Open-Meteo.com").
 * 예보는 오늘부터 16일 뒤까지(과거 92일도 같은 API), 그 밖의 날짜는 작년 같은 날 실제 날씨(archive API)를 보여준다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.weather")
public class WeatherProperties {

    private boolean enabled = true;
    private String forecastUrl = "https://api.open-meteo.com/v1/forecast";
    private String archiveUrl = "https://archive-api.open-meteo.com/v1/archive";
    private int timeoutSeconds = 5;
    /** 예보로 볼 수 있는 마지막 날 = 오늘 + 이 값 */
    private int forecastDays = 15;
    /** 예보 API가 받아 주는 과거 날짜 */
    private int pastDays = 92;
    /** 같은 위치·날짜를 다시 묻지 않는 시간 (예보 / 작년 날씨) */
    private int forecastCacheMinutes = 180;
    private int archiveCacheHours = 168;
    /** 비 판단: 예보는 강수 확률 이 % 이상, 작년 날씨는 강수량 이 mm 이상 */
    private int rainProbabilityPercent = 60;
    private double rainMillimeters = 5.0;
}
