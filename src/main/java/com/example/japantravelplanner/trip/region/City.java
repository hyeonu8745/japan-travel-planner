package com.example.japantravelplanner.trip.region;

import com.example.japantravelplanner.global.util.GeoUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 확장 대상 19개 도시 (11-08 리팩터링). 중심 좌표·반경은 데이터 정리(data_clean.py CITY_RADIUS)와 같은 값이다.
 * DB에는 이름(TOKYO 등) 문자열로 저장한다.
 * 지금 일정 생성은 도쿄만 지역(TripZone)이 있다. 다른 도시는 11월 확장 때 지역과 데이터를 넣는다.
 */
public enum City {

    TOKYO("도쿄", Region.KANTO, 35.681, 139.767, 25),
    YOKOHAMA("요코하마", Region.KANTO, 35.45, 139.63, 15),
    KAMAKURA("가마쿠라", Region.KANTO, 35.32, 139.55, 8),
    HAKONE("하코네", Region.KANTO, 35.23, 139.05, 15),
    KAWAGUCHIKO("가와구치코", Region.KANTO, 35.50, 138.76, 15),
    OSAKA("오사카", Region.KANSAI, 34.69, 135.50, 15),
    KYOTO("교토", Region.KANSAI, 35.01, 135.77, 15),
    KOBE("고베", Region.KANSAI, 34.69, 135.19, 15),
    NARA("나라", Region.KANSAI, 34.685, 135.83, 12),
    NAGOYA("나고야", Region.CHUBU, 35.17, 136.91, 15),
    KANAZAWA("가나자와", Region.CHUBU, 36.56, 136.65, 15),
    TAKAYAMA("다카야마", Region.CHUBU, 36.14, 137.25, 10),
    HIROSHIMA("히로시마", Region.HIROSHIMA, 34.39, 132.46, 20),
    FUKUOKA("후쿠오카", Region.KYUSHU_NORTH, 33.59, 130.40, 15),
    BEPPU("벳푸", Region.KYUSHU_NORTH, 33.28, 131.49, 10),
    YUFUIN("유후인", Region.KYUSHU_NORTH, 33.265, 131.36, 8),
    SAPPORO("삿포로", Region.HOKKAIDO, 43.06, 141.35, 15),
    OTARU("오타루", Region.HOKKAIDO, 43.19, 141.0, 10),
    NAHA("나하", Region.OKINAWA, 26.21, 127.68, 12);

    private final String label;
    private final Region region;
    private final double centerLat;
    private final double centerLng;
    private final double radiusKm;

    City(String label, Region region, double centerLat, double centerLng, double radiusKm) {
        this.label = label;
        this.region = region;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.radiusKm = radiusKm;
    }

    public String label() {
        return label;
    }

    public Region region() {
        return region;
    }

    public double centerLat() {
        return centerLat;
    }

    public double centerLng() {
        return centerLng;
    }

    /** 도시 범위 안인지 (중심에서 반경 안) */
    public boolean contains(double lat, double lng) {
        return GeoUtils.distanceMeters(centerLat, centerLng, lat, lng) <= radiusKm * 1000;
    }

    public static List<City> inRegion(Region region) {
        return Arrays.stream(values()).filter(c -> c.region == region).toList();
    }

    /** 이름으로 찾기 (대소문자 무시). 비어 있거나 모르는 이름이면 empty */
    public static Optional<City> of(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String c = code.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(city -> city.name().equals(c)).findFirst();
    }
}
