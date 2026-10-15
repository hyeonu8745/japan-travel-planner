package com.example.japantravelplanner.trip.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 도착·출발 공항 (10-15). DB에는 코드 문자열(NRT, HND)로 저장한다 — enum 컬럼은 CHECK 제약이 생겨
 * 확장 때 공항을 더하면 SQL이 필요하므로 (6장).
 * 확장(11월) 때 도시별 공항(KIX, ITM, FUK, CTS 등)을 여기에 더한다.
 *
 * @param transferMinutes 공항 ↔ 시내 숙소 대중교통 대표 시간(분). Google은 일본 대중교통 경로를 주지 않아서
 *                        대표값을 쓴다 (나리타: 스카이라이너·나리타 익스프레스 + 갈아타기, 하네다: 게이큐·모노레일 + 갈아타기)
 */
public enum Airport {

    NRT("나리타 공항", 80, 35.7720, 140.3929),
    HND("하네다 공항", 45, 35.5494, 139.7798);

    private final String label;
    private final int transferMinutes;
    private final double lat;
    private final double lng;

    Airport(String label, int transferMinutes, double lat, double lng) {
        this.label = label;
        this.transferMinutes = transferMinutes;
        this.lat = lat;
        this.lng = lng;
    }

    public String label() {
        return label;
    }

    public int transferMinutes() {
        return transferMinutes;
    }

    public double lat() {
        return lat;
    }

    public double lng() {
        return lng;
    }

    /** 코드로 찾기 (대소문자 무시). 비어 있거나 모르는 코드면 empty */
    public static Optional<Airport> of(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String c = code.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(a -> a.name().equals(c)).findFirst();
    }

    public static boolean isKnownOrBlank(String code) {
        return code == null || code.isBlank() || of(code).isPresent();
    }

    /** 저장할 코드 (대문자, 비어 있으면 null) */
    public static String normalize(String code) {
        return of(code).map(Enum::name).orElse(null);
    }
}
