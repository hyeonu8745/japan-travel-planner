package com.example.japantravelplanner.dataimport;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 관광지 Google 장소 ID·평점 받기 설정 ({@code app.place-lookup.*}).
 * 한 번 실행에 쓰는 호출 수에 상한을 둬서 월 무료 한도(Text Search Pro 5,000 · Enterprise 1,000)를 넘지 않게 한다.
 * 상한에 닿으면 멈추고, 다시 실행하면 확인하지 않은 곳부터 이어서 한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.place-lookup")
public class PlaceLookupProperties {

    /** 한 번 실행에 쓰는 Text Search Pro(장소 ID만) 최대 호출 수 */
    private int maxProCalls = 3600;

    /** 한 번 실행에 쓰는 Text Search Enterprise(평점 포함) 최대 호출 수 */
    private int maxEnterpriseCalls = 800;

    /** 평점까지 받을 인기도 기준 (꼭 가는 곳은 인기도와 상관없이 받는다) */
    private int ratingMinPopularity = 10;

    /** 검색할 때 이 원 안을 우선한다 */
    private double searchRadiusMeters = 1000;

    /** 같은 곳으로 볼 최대 거리 (넓은 공원·절은 Google 핀과 OSM 중심이 수백 m 떨어진다) */
    private double maxDistanceMeters = 1000;

    /** 이름 비슷한 정도 기준 (0~1) */
    private double minNameSimilarity = 0.7;

    /** 가타카나 ↔ 영어 이름으로 맞출 때 허용 거리 */
    private double loanwordMaxDistanceMeters = 300;

    /** 가타카나 ↔ 영어 자음 뼈대 비슷한 정도 기준 */
    private double loanwordMinSimilarity = 0.6;

    /** 검색 결과 몇 개를 비교할지 */
    private int pageSize = 3;

    /** 호출 사이 쉬는 시간 (밀리초) */
    private long delayMillis = 100;
}
