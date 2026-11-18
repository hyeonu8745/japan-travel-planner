package com.example.japantravelplanner.itinerary.fare;

import com.example.japantravelplanner.trip.region.City;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 교통비 추정. Google Routes는 일본 대중교통 경로·요금을 주지 않아서 이동 시간처럼 자체 추정한다.
 * 대중교통 구간 거리로 지하철 거리별 운임(IC 카드, 성인)을 찾는다. 같은 거리라도 도쿄 메트로가 싸고 도에이가 비싸서
 * 둘을 최소·최대로 쓴다. 갈아타기(메트로 ↔ 도에이 ↔ JR)는 기본요금이 다시 붙어 더 나올 수 있다 (화면 안내).
 * 값은 각 운영사 운임표 기준 (도쿄 메트로·도에이 지하철 공식 사이트).
 *
 * 교통비 계산은 도시 운임({@link CityFare})을 받는다. 도쿄는 아래 칸(메트로·도에이·Tokyo Subway Ticket),
 * 다른 도시는 {@code app.fare.cities.<도시>} (11-18, 교통비 조사 문서의 공식 확인 값):
 * - 거리별 운임 도시(오사카·고베·후쿠오카·삿포로·나고야)는 지하철 운임표 하나를 최소·최대로
 * - 버스가 중심인 곳(교토·요코하마)은 버스 균일 요금을 최소, 지하철 운임표를 최대로
 * - 정액(히로시마 노면전차·나라·가나자와·오타루 버스, 다카야마 100~210)은 한 구간 운임표
 * - 패스로 다니는 곳(하코네·가와구치코·벳푸·유후인)은 타는 날마다 패스 값(dayYenIfRide)
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

    /** 도쿄 밖 도시 운임 (app.fare.cities.OSAKA 등) */
    private Map<City, CitySettings> cities = new EnumMap<>(City.class);

    /** 도시의 대중교통 운임. 값이 없는 도시면 empty */
    public Optional<CityFare> forCity(City city) {
        if (city == City.TOKYO) {
            return Optional.of(new CityFare(metroBands, toeiBands, pass24Yen, pass48Yen, pass72Yen,
                    "Tokyo Subway Ticket", 0));
        }
        CitySettings c = cities.get(city);
        return c == null ? Optional.empty() : Optional.of(c.toCityFare());
    }

    /**
     * 도시 하나의 운임: 같은 거리의 싼 쪽(minBands)·비싼 쪽(maxBands) 거리별 운임과 24·48·72시간권 값
     * (도쿄: 메트로·도에이, Tokyo Subway Ticket). 패스 값이 0이면 그 패스가 없는 도시.
     *
     * @param passName     패스 이름 (화면 안내, 예: 엔조이 에코 카드)
     * @param dayYenIfRide 0보다 크면 구간 운임 대신, 대중교통을 타는 날마다 이 값 (패스로만 다니는 곳: 하코네 프리패스)
     */
    public record CityFare(List<Band> minBands, List<Band> maxBands, int pass24Yen, int pass48Yen, int pass72Yen,
                           String passName, int dayYenIfRide) {

        /** 도쿄 모양 (이름·패스 전용 없이) */
        public CityFare(List<Band> minBands, List<Band> maxBands, int pass24Yen, int pass48Yen, int pass72Yen) {
            this(minBands, maxBands, pass24Yen, pass48Yen, pass72Yen, null, 0);
        }
    }

    /** 설정 파일의 도시 운임 한 줄 (maxBands 가 없으면 minBands 와 같다) */
    @Getter
    @Setter
    public static class CitySettings {
        private List<Band> minBands = new ArrayList<>();
        private List<Band> maxBands = new ArrayList<>();
        private int pass24Yen;
        private int pass48Yen;
        private int pass72Yen;
        private String passName;
        private int dayYenIfRide;

        /** 계산에 쓰는 모양으로 */
        CityFare toCityFare() {
            List<Band> min = minBands.isEmpty() ? List.of(new Band(1, 0)) : minBands;
            List<Band> max = maxBands.isEmpty() ? min : maxBands;
            return new CityFare(min, max, pass24Yen, pass48Yen, pass72Yen, passName, dayYenIfRide);
        }
    }

    @Getter
    @Setter
    public static class Band {
        private int maxKm;
        private int yen;

        /** 설정 파일 바인딩용 빈 생성자 */
        public Band() {
        }

        /**
         * @param maxKm 이 거리(km)까지
         * @param yen 운임(엔)
         */
        public Band(int maxKm, int yen) {
            this.maxKm = maxKm;
            this.yen = yen;
        }
    }
}
