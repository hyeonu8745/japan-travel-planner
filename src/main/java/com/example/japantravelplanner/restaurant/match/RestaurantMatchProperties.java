package com.example.japantravelplanner.restaurant.match;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 타베로그 식당 ↔ Google 장소 매칭 기준 (app.restaurant-match).
 * 기준값은 10-09 표본 50곳 측정 결과로 정했다: 거리 300m·이름 0.7 + 지점 이름 충돌 거절 → 42곳 연결, 잘못 연결 0곳
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.restaurant-match")
public class RestaurantMatchProperties {

    /** false면 Google에 확인하지 않는다 (모든 식당이 지금처럼 후보) */
    private boolean enabled = true;

    /** 검색할 때 이 반경 안을 우선해달라고 Google에 알려준다 (반경 밖 결과도 올 수 있음) */
    private double searchRadiusMeters = 300;

    /** 우리 좌표와 이 거리 안이어야 같은 가게로 본다. 도쿄역 지하상가 가게는 좌표가 200~260m 어긋났다 */
    private double maxDistanceMeters = 300;

    /** 이름 비슷한 정도(0~1)가 이 값 이상이어야 같은 가게로 본다 */
    private double minNameSimilarity = 0.7;

    /** 검색 결과를 몇 개까지 받아서 비교할지 (1순위가 아닌 결과가 맞는 경우가 있었다) */
    private int pageSize = 3;

    /** 일정 생성 때 "확인 → 빠진 식당이 있으면 다시 계산"을 몇 번까지 할지 */
    private int verifyRounds = 2;
}
