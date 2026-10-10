package com.example.japantravelplanner.itinerary.travel;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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