package com.example.japantravelplanner.itinerary.transit;

import java.util.Map;
import java.util.Optional;

/**
 * 넓은 곳의 입구 좌표 (11-22). 관광지 좌표가 넓은 부지의 가운데·한쪽이라 가까운 역이 실제로 내리는 역과 다를 때,
 * 구간 경로·길찾기 링크는 입구 좌표로 찾는다 (일정 시간 계산은 그대로).
 *
 * - USJ: 좌표가 파크 서쪽이라 사쿠라지마역이 더 가깝게 나온다 → 정문(유니버설시티역 쪽)
 */
public final class Entrances {

    private Entrances() {
    }

    /** 관광지 이름 → 입구 [위도, 경도] */
    static final Map<String, double[]> BY_NAME = Map.of(
            "ユニバーサル・スタジオ・ジャパン", new double[]{34.6662, 135.4367});

    public static Optional<double[]> of(String attractionName) {
        return Optional.ofNullable(attractionName == null ? null : BY_NAME.get(attractionName));
    }
}
