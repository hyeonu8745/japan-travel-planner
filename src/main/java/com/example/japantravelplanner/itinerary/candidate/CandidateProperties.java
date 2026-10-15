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

    // 하루 이동 범위 (10-11 사용자 결정: 여행 가면 하루에 두 지역은 돈다).
    // 그날 중심 지역에서 이 거리 안에 중심이 있는 이웃 지역 하나를 함께 돌고, 그 지역 관광지도 이 거리 안이면 후보.
    // 0이면 예전처럼 한 지역만
    private double dayRangeCompactKm = 7;    // 가까운 곳 위주(이동 제약·적게 걷기)
    private double dayRangeTransitKm = 10;   // 대중교통
    private double dayRangeCarKm = 12;       // 렌터카
    // 함께 돌 이웃 지역 고르기: 그 지역 상위 관광지 점수 합 - 중심 사이 거리(km) × 이 값
    private double partnerDistancePenaltyPerKm = 0.15;
    // 다른 날의 중심 지역이거나 이미 다른 날 함께 돈 지역이면 이만큼 감점 (같은 동네가 날마다 반복되지 않게)
    private double partnerReusePenalty = 0.5;

    // 마지막 날 = 숙소 근처 쇼핑 (10-11 사용자 결정: 나리타·하네다로 가니까 숙소 근처에서 쇼핑하다 간다).
    // 여행 조건에 비행기 출발 시각이 있을 때만. 숙소에서 이 거리 안 관광지만, 이웃 지역 없이, 쇼핑에 가산, 놀이공원 등은 제외
    private double lastDayRadiusMeters = 2000;
    private double lastDayShoppingBonus = 0.3;
}
