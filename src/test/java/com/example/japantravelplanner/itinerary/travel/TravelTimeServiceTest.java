package com.example.japantravelplanner.itinerary.travel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TravelTimeServiceTest {

    private final TravelTimeService service = new TravelTimeService(new TravelProperties(), null);

    private final TravelPoint hotel = new TravelPoint("숙소(가부키초)", 35.6951588, 139.7020539);
    private final TravelPoint cat = new TravelPoint("동쪽 출구 고양이", 35.6926516, 139.700803);     // 약 300m
    private final TravelPoint gyoen = new TravelPoint("신주쿠교엔", 35.6851763, 139.7100517);        // 약 1.3km
    private final TravelPoint senso = new TravelPoint("센소지", 35.7147651, 139.7966553);            // 약 9km

    @Test
    void 가까운_곳은_걸어간다() {
        TravelLeg leg = service.buildMatrix(List.of(hotel, cat), LegMode.TRANSIT, 20).get(0, 1);
        assertThat(leg.mode()).isEqualTo(LegMode.WALK);
        assertThat(leg.minutes()).isBetween(3, 7);
    }

    @Test
    void 먼_곳은_대중교통으로_간다() {
        TravelLeg leg = service.buildMatrix(List.of(hotel, senso), LegMode.TRANSIT, 20).get(0, 1);
        assertThat(leg.mode()).isEqualTo(LegMode.TRANSIT);
        assertThat(leg.minutes()).isBetween(30, 60);
    }

    @Test
    void 도보_기준이_짧으면_같은_거리도_대중교통을_쓴다() {
        // 1.3km는 걸으면 20분 남짓. 기준 20분이면 경계, 기준 10분이면 대중교통
        TravelLeg relaxed = service.buildMatrix(List.of(hotel, gyoen), LegMode.TRANSIT, 10).get(0, 1);
        assertThat(relaxed.mode()).isEqualTo(LegMode.TRANSIT);
    }

    // ── 렌트카 ──

    @Test
    void 렌트카여도_가까운_곳은_걸어간다() {
        TravelLeg leg = service.buildMatrix(List.of(hotel, cat), LegMode.DRIVE, 20).get(0, 1);
        assertThat(leg.mode()).isEqualTo(LegMode.WALK);
        assertThat(leg.minutes()).isBetween(3, 7);
    }

    @Test
    void 숙소_바로_옆은_렌트카여도_차로_가지_않는다() {
        // 10-08 Postman에서 발견: 숙소에서 22m인 고질라 헤드가 "차로 6분"(주차 5분 포함)으로 나왔다
        TravelPoint godzilla = new TravelPoint("고질라 헤드", 35.6950521, 139.7019117);

        TravelLeg leg = service.buildMatrix(List.of(hotel, godzilla), LegMode.DRIVE, 20).get(0, 1);

        assertThat(leg.mode()).isEqualTo(LegMode.WALK);
        assertThat(leg.minutes()).isEqualTo(1);
    }

    @Test
    void 렌트카는_먼_곳을_운전한다() {
        TravelLeg leg = service.buildMatrix(List.of(hotel, senso), LegMode.DRIVE, 20).get(0, 1);
        assertThat(leg.mode()).isEqualTo(LegMode.DRIVE);
    }

    @Test
    void 렌트카도_도보_기준이_짧으면_같은_거리를_운전한다() {
        // 1.3km: 기준 20분이면 경계라 걸을 수도 있지만, 여유 속도·이동 제약(기준 10분)이면 운전
        TravelLeg relaxed = service.buildMatrix(List.of(hotel, gyoen), LegMode.DRIVE, 10).get(0, 1);
        assertThat(relaxed.mode()).isEqualTo(LegMode.DRIVE);
    }

    @Test
    void 렌트카여도_대중교통으로_가지는_않는다() {
        TravelMatrix matrix = service.buildMatrix(List.of(hotel, cat, gyoen, senso), LegMode.DRIVE, 10);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i != j) assertThat(matrix.get(i, j).mode()).isNotEqualTo(LegMode.TRANSIT);
            }
        }
    }

    @Test
    void 자기_자신은_0분() {
        assertThat(service.buildMatrix(List.of(hotel, cat), LegMode.TRANSIT, 20).minutes(0, 0)).isZero();
    }
}