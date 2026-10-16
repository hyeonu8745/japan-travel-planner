package com.example.japantravelplanner.weather;

import java.time.LocalDate;

/** Open-Meteo 하루 값 (빈 값은 null) */
public record DailyWeather(LocalDate date, Integer weatherCode, Double tempMax, Double tempMin,
                           Integer rainProbability, Double rainMm) {
}
