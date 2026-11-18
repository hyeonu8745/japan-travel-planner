package com.example.japantravelplanner.trip.region;

import com.example.japantravelplanner.global.util.GeoUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 확장 대상 19개 도시. 중심 좌표·반경은 데이터 정리(data_clean.py CITY_RADIUS)와 같은 값이다.
 * DB에는 이름(TOKYO 등) 문자열로 저장한다.
 * 숙소 좌표가 들어가는 도시가 여행의 기준 도시가 된다 ({@link #containing}, 11-18).
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

    /** 영어 이름 (예: Tokyo, Kawaguchiko). 포스터 그림 지시에 쓴다 */
    public String englishName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    /** 한국어 이름 (예: 도쿄) */
    public String label() {
        return label;
    }

    /** 이 도시가 속한 권역 */
    public Region region() {
        return region;
    }

    /** 도시 중심 위도 */
    public double centerLat() {
        return centerLat;
    }

    /** 도시 중심 경도 */
    public double centerLng() {
        return centerLng;
    }

    /** 도시 범위 안인지 (중심에서 반경 안) */
    public boolean contains(double lat, double lng) {
        return GeoUtils.distanceMeters(centerLat, centerLng, lat, lng) <= radiusKm * 1000;
    }

    /**
     * 좌표가 범위 안에 들어가는 도시 중 중심이 가장 가까운 곳 (도쿄·요코하마처럼 범위가 겹치면 가까운 쪽).
     * 어느 도시 범위에도 없으면 empty (서비스 지역 밖)
     */
    public static Optional<City> containing(double lat, double lng) {
        return Arrays.stream(values())
                .filter(city -> city.contains(lat, lng))
                .min(java.util.Comparator.comparingDouble(
                        city -> GeoUtils.distanceMeters(city.centerLat, city.centerLng, lat, lng)));
    }

    /**
     * 숙소 이름·주소 글자로 도시 짐작 (여행을 만들 때, 좌표를 찾기 전). 일본어·한국어 도시 이름이 있으면 그 도시.
     * 「東京都」 안의 「京都」처럼 겹치는 이름은 막는다. 여러 도시가 보이면 처음 맞는 것, 없으면 empty
     */
    public static Optional<City> guess(String... texts) {
        StringBuilder sb = new StringBuilder();
        for (String t : texts) if (t != null) sb.append(t).append(' ');
        String text = sb.toString();
        for (var entry : NAME_PATTERNS) {
            if (entry.getValue().matcher(text).find()) return Optional.of(entry.getKey());
        }
        return Optional.empty();
    }

    /** 도시 이름 글자 (guess). 한국어 "나라"는 "우리나라" 같은 말과 겹쳐서 나라시·나라현·나라역만 */
    private static final List<java.util.Map.Entry<City, java.util.regex.Pattern>> NAME_PATTERNS = List.of(
            java.util.Map.entry(TOKYO, java.util.regex.Pattern.compile("東京|도쿄")),
            java.util.Map.entry(YOKOHAMA, java.util.regex.Pattern.compile("横浜|요코하마")),
            java.util.Map.entry(KAMAKURA, java.util.regex.Pattern.compile("鎌倉|가마쿠라")),
            java.util.Map.entry(HAKONE, java.util.regex.Pattern.compile("箱根|하코네")),
            java.util.Map.entry(KAWAGUCHIKO, java.util.regex.Pattern.compile("河口湖|가와구치코")),
            java.util.Map.entry(OSAKA, java.util.regex.Pattern.compile("大阪|오사카")),
            java.util.Map.entry(KYOTO, java.util.regex.Pattern.compile("(?<!東)京都|교토")),
            java.util.Map.entry(KOBE, java.util.regex.Pattern.compile("神戸|고베")),
            java.util.Map.entry(NARA, java.util.regex.Pattern.compile("奈良|나라시|나라현|나라역")),
            java.util.Map.entry(NAGOYA, java.util.regex.Pattern.compile("名古屋|나고야")),
            java.util.Map.entry(KANAZAWA, java.util.regex.Pattern.compile("金沢|가나자와")),
            java.util.Map.entry(TAKAYAMA, java.util.regex.Pattern.compile("高山市|飛騨高山|다카야마")),
            java.util.Map.entry(HIROSHIMA, java.util.regex.Pattern.compile("広島|히로시마")),
            java.util.Map.entry(FUKUOKA, java.util.regex.Pattern.compile("福岡|博多|후쿠오카|하카타")),
            java.util.Map.entry(BEPPU, java.util.regex.Pattern.compile("別府|벳푸")),
            java.util.Map.entry(YUFUIN, java.util.regex.Pattern.compile("由布院|湯布院|由布市|유후인")),
            java.util.Map.entry(SAPPORO, java.util.regex.Pattern.compile("札幌|삿포로")),
            java.util.Map.entry(OTARU, java.util.regex.Pattern.compile("小樽|오타루")),
            java.util.Map.entry(NAHA, java.util.regex.Pattern.compile("那覇|나하")));

    /** 권역에 속한 도시 목록 (선언 순서) */
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
