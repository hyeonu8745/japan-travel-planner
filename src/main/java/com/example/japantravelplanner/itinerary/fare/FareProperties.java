package com.example.japantravelplanner.itinerary.fare;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 교통비 추정 (10-16). Google Routes는 일본 대중교통 경로·요금을 주지 않아서(10-02 확인) 이동 시간처럼 자체 추정한다.
 * 대중교통 구간 거리로 지하철 거리별 운임(IC 카드, 성인)을 찾는다. 같은 거리라도 도쿄 메트로가 싸고 도에이가 비싸서
 * 둘을 최소·최대로 쓴다. 갈아타기(메트로 ↔ 도에이 ↔ JR)는 기본요금이 다시 붙어 더 나올 수 있다 (화면 안내).
 * 값은 10-16 각 운영사 운임표 기준 (도쿄 메트로·도에이 지하철 공식 사이트).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.fare")
public class FareProperties {

    /** 도쿄 메트로 IC 운임: maxKm까지 yen (1~6km 178, 7~11km 209, 12~19km 252, 20~27km 293, 28~40km 324) */
    private List<Band> metroBands = new ArrayList<>(List.of(
            new Band(6, 178), new Band(11, 209), new Band(19, 252), new Band(27, 293), new Band(40, 324)));
    /** 도에이 지하철 IC 운임 (1~4km 178, 5~9km 220, 10~15km 272, 16~21km 325, 22~27km 377, 28~46km 430) */
    private List<Band> toeiBands = new ArrayList<>(List.of(
            new Band(4, 178), new Band(9, 220), new Band(15, 272), new Band(21, 325), new Band(27, 377), new Band(46, 430)));
    /** Tokyo Subway Ticket (메트로·도에이 전 노선, 외국인 여행자용) 24·48·72시간권 */
    private int pass24Yen = 1000;
    private int pass48Yen = 1500;
    private int pass72Yen = 2000;

    @Getter
    @Setter
    public static class Band {
        private int maxKm;
        private int yen;

        public Band() {
        }

        public Band(int maxKm, int yen) {
            this.maxKm = maxKm;
            this.yen = yen;
        }
    }
}
