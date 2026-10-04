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
    // 이 거리(미터) 안의 관광지는 같은 장소(하위 명소)로 보고 점수 높은 하나만 남긴다
    private double sameSpotMeters = 150;
}