package com.example.japantravelplanner.itinerary.travel;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 이동 시간 자체 계산 설정 ({@code app.travel.*}): 걷는 기준 시간, 걷기·대중교통·차 속도와 우회 비율, 타고 내리는 시간. Google Routes 사용은 기본 꺼짐.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.travel")
public class TravelProperties {

    private boolean useGoogleRoutes = false;
    private int walkThresholdMinutes = 30;
    private int relaxedWalkThresholdMinutes = 10;
    private double walkMetersPerMinute = 80;
    private double walkDetour = 1.25;
    private double transitMetersPerMinute = 333;
    private double transitDetour = 1.3;
    private int transitOverheadMinutes = 8;
    private double driveMetersPerMinute = 416;
    private int driveOverheadMinutes = 5;
}