package com.example.japantravelplanner.itinerary.candidate;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.candidate")
public class CandidateProperties {

    private int priorReviews = 200;
    private double interestBonus = 0.3;
    private int extraCandidates = 2;
}