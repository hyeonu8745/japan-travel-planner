package com.example.japantravelplanner.trip.entity;

import com.example.japantravelplanner.trip.region.City;

import java.util.List;
import java.util.Optional;

/**
 * 공항 ↔ 도시 이동 (10-16). 확장 때 한 공항이 여러 도시(간사이 → 오사카·교토·고베·나라),
 * 한 도시가 여러 공항(고베 = 고베 공항·간사이)이 되므로 "공항 + 도시" 짝마다 둔다 (확장 계획 0-7).
 * 지금은 도쿄 값만 있다. 11월 확장 때 공항 + 도시 짝마다 넣는다 (교통비 조사 문서).
 *
 * 값은 대표값이다 (10-16 확인, 각 운영사 사이트·여행 안내):
 * - 나리타 ↔ 도쿄: 게이세이 액세스 특급 1,270엔·공항버스 1,300엔(약 60~100분) / 스카이라이너 2,570엔(닛포리까지 약 36분)·나리타 익스프레스 3,070엔
 * - 하네다 ↔ 도쿄: 게이큐 시나가와까지 330엔(IC 327엔, 약 15분) / 도쿄 모노레일 + JR 약 660엔
 *
 * @param minutes    공항 ↔ 시내 숙소 대중교통 대표 시간 (갈아타기 포함)
 * @param cheapYen   저렴한 방법 1인 요금 (공항 → 시내 역)
 * @param fastYen    빠른 방법 1인 요금
 */
public record AirportAccess(Airport airport, City city, int minutes,
                            int cheapYen, String cheapLabel, int fastYen, String fastLabel) {

    private static final List<AirportAccess> TABLE = List.of(
            new AirportAccess(Airport.NRT, City.TOKYO, 80,
                    1270, "게이세이 액세스 특급·공항버스 (약 60~100분)", 2570, "스카이라이너 (닛포리까지 약 36분)"),
            new AirportAccess(Airport.HND, City.TOKYO, 45,
                    330, "게이큐선 (시나가와까지 약 15분)", 660, "도쿄 모노레일 + JR (약 25~30분)"));

    public static Optional<AirportAccess> of(Airport airport, City city) {
        return TABLE.stream().filter(a -> a.airport == airport && a.city == city).findFirst();
    }
}
