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
    // 리뷰가 이 수보다 적은 관광지는 일정 후보에서 뺀다 (비석·작은 골목·언덕 같은 곳이라 일정에 넣을 만하지 않음)
    private int minReviews = 100;

    // 하루 관광지를 고를 때 지역 안에서 섞기 (10-10: 점수순으로만 고르면 한 공원 안 명소처럼 주변만 돌았다)
    // 같은 종류(PlaceKind)를 이미 고른 수만큼 감점. 종류가 OTHER면 감점하지 않는다 (교차로·동상·거리처럼 서로 다르다)
    private double sameKindPenalty = 0.15;
    // 이미 고른 곳에서 이 거리(미터) 안이면 감점 (하루가 한 동네에 몰리지 않게)
    private double nearMeters = 500;
    private double nearPenalty = 0.2;
}