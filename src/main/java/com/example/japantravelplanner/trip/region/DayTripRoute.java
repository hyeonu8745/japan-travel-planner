package com.example.japantravelplanner.trip.region;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 숙소 도시 ↔ 당일치기 도시 이동 (11-22 확장 5단계). 한 줄 = 두 도시 사이 대표 경로 하나, 양쪽 방향에 같은 값을 쓴다.
 * 시간·운임은 역 ↔ 역 (대인 1인 편도, 종이 승차권). 숙소 ↔ 역 이동은 계산할 때 따로 더한다 (DayTrips).
 *
 * 값 출처 (claude/도시별_교통비_조사.md 4-3장): 에키탄 검색 2026-11 (JR 도쿄권 2026-03 개정 반영),
 * 하코네 로맨스카 특급권은 하코네나비, 고속버스는 교통비 조사 4장. {@link #verified()} 가 false 인 값은 다른 값을 더하고 빼서 구했거나
 * 공항 출발 값과 같다고 본 것이라 다시 확인한다.
 */
public enum DayTripRoute {

    OSAKA_KYOTO(City.OSAKA, new Station("오사카역", 34.7025, 135.4959),
            City.KYOTO, new Station("교토역", 34.9858, 135.7588), 28, 580, "JR 신쾌속", true),
    OSAKA_KOBE(City.OSAKA, new Station("오사카역", 34.7025, 135.4959),
            City.KOBE, new Station("산노미야역", 34.6946, 135.1955), 22, 420, "JR 신쾌속", true),
    OSAKA_NARA(City.OSAKA, new Station("오사카난바역", 34.6666, 135.5005),
            City.NARA, new Station("긴테쓰나라역", 34.6843, 135.8279), 39, 680, "긴테쓰 나라선 쾌속급행", true),
    KYOTO_NARA(City.KYOTO, new Station("교토역", 34.9858, 135.7588),
            City.NARA, new Station("긴테쓰나라역", 34.6843, 135.8279), 46, 760, "긴테쓰 교토선 급행", true),
    KYOTO_KOBE(City.KYOTO, new Station("교토역", 34.9858, 135.7588),
            City.KOBE, new Station("산노미야역", 34.6946, 135.1955), 57, 1110, "JR 신쾌속", true),
    TOKYO_YOKOHAMA(City.TOKYO, new Station("도쿄역", 35.6812, 139.7671),
            City.YOKOHAMA, new Station("요코하마역", 35.4658, 139.6223), 26, 530, "JR 도카이도선", true),
    TOKYO_KAMAKURA(City.TOKYO, new Station("도쿄역", 35.6812, 139.7671),
            City.KAMAKURA, new Station("가마쿠라역", 35.3190, 139.5505), 55, 1040, "JR 요코스카선", true),
    YOKOHAMA_KAMAKURA(City.YOKOHAMA, new Station("요코하마역", 35.4658, 139.6223),
            City.KAMAKURA, new Station("가마쿠라역", 35.3190, 139.5505), 24, 440, "JR 쇼난신주쿠라인·요코스카선", true),
    // 오다큐 운임 1,270 + 로맨스카 특급권 1,200
    TOKYO_HAKONE(City.TOKYO, new Station("신주쿠역", 35.6896, 139.7006),
            City.HAKONE, new Station("하코네유모토역", 35.2326, 139.1060), 85, 2470, "오다큐 로맨스카", true),
    TOKYO_KAWAGUCHIKO(City.TOKYO, new Station("바스타 신주쿠", 35.6885, 139.7005),
            City.KAWAGUCHIKO, new Station("가와구치코역", 35.4986, 138.7686), 110, 2200, "고속버스 (예약)", true),
    SAPPORO_OTARU(City.SAPPORO, new Station("삿포로역", 43.0687, 141.3508),
            City.OTARU, new Station("오타루역", 43.1977, 140.9937), 35, 800, "JR 쾌속 에어포트", true),
    // 하카타 BT ↔ 유후인 약 2시간 7분, 운임은 예약 사이트의 이 노선 최대 운임 3,250 (후쿠오카 쪽 끝 정류장, 11-22)
    FUKUOKA_YUFUIN(City.FUKUOKA, new Station("하카타 버스터미널", 33.5904, 130.4205),
            City.YUFUIN, new Station("유후인역 버스센터", 33.2663, 131.3557), 125, 3250, "고속버스 유후인호 (예약)", true),
    FUKUOKA_BEPPU(City.FUKUOKA, new Station("니시테쓰 덴진 고속버스터미널", 33.5893, 130.3990),
            City.BEPPU, new Station("벳푸역", 33.2797, 131.5004), 170, 3250, "고속버스 도요노쿠니호 (예약)", true),
    BEPPU_YUFUIN(City.BEPPU, new Station("벳푸역", 33.2797, 131.5004),
            City.YUFUIN, new Station("유후인역", 33.2663, 131.3557), 97, 1300, "JR 닛포본선·규다이본선 (오이타 환승)", true),
    NAGOYA_TAKAYAMA(City.NAGOYA, new Station("메이테쓰 버스센터", 35.1700, 136.8846),
            City.TAKAYAMA, new Station("다카야마 노히 버스센터", 36.1412, 137.2508), 165, 3600, "고속버스 (예약)", true);

    /** 역(또는 버스터미널) 이름과 좌표. 당일치기 날은 도착역에서 관광을 시작하고 그 역으로 돌아온다 */
    public record Station(String name, double lat, double lng) {
    }

    private final City a;
    private final Station aStation;
    private final City b;
    private final Station bStation;
    private final int minutes;
    private final int fareYen;
    private final String label;
    private final boolean verified;

    DayTripRoute(City a, Station aStation, City b, Station bStation, int minutes, int fareYen, String label, boolean verified) {
        this.a = a;
        this.aStation = aStation;
        this.b = b;
        this.bStation = bStation;
        this.minutes = minutes;
        this.fareYen = fareYen;
        this.label = label;
        this.verified = verified;
    }

    /** 두 도시 사이 경로 (방향 상관없이). 없으면 empty */
    public static Optional<DayTripRoute> between(City base, City target) {
        if (base == null || target == null || base == target) return Optional.empty();
        return Arrays.stream(values()).filter(r -> r.connects(base, target)).findFirst();
    }

    /** 이 도시에 묵을 때 당일치기로 갈 수 있는 경로 (가까운 순) */
    public static List<DayTripRoute> from(City base) {
        return Arrays.stream(values())
                .filter(r -> r.a == base || r.b == base)
                .sorted(Comparator.comparingInt(DayTripRoute::minutes))
                .toList();
    }

    /** 이 도시에 묵을 때 당일치기로 갈 수 있는 도시 (가까운 순) */
    public static List<City> targets(City base) {
        return from(base).stream().map(r -> r.other(base)).toList();
    }

    private boolean connects(City x, City y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    /** 반대쪽 도시 */
    public City other(City city) {
        return city == a ? b : a;
    }

    /** 이 도시 쪽 역 */
    public Station stationOf(City city) {
        return city == a ? aStation : bStation;
    }

    /** 역 ↔ 역 편도 시간(분) */
    public int minutes() {
        return minutes;
    }

    /** 역 ↔ 역 편도 운임(엔, 대인 1인) */
    public int fareYen() {
        return fareYen;
    }

    /** 타는 것 (예: JR 신쾌속, 고속버스 (예약)) */
    public String label() {
        return label;
    }

    /** 공식·검색 사이트 값 그대로면 true, 다른 값으로 구했거나 다시 확인할 값이면 false */
    public boolean verified() {
        return verified;
    }
}
