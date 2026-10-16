package com.example.japantravelplanner.trip.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 도착·출발 공항 (10-15). DB에는 코드 문자열(NRT, HND)로 저장한다 — enum 컬럼은 CHECK 제약이 생겨
 * 확장 때 공항을 더하면 SQL이 필요하므로 (6장).
 * 확장(11월) 때 공항을 더한다 (확장 계획 0-7: KIX, UKB, FUK, CTS, NGO, OKA, HIJ, KMQ).
 * 이동 시간·운임은 공항 + 도시 짝마다 {@link AirportAccess}에 둔다.
 * 이름은 공식 한국어 표기 (10-15 정리: 나리타 국제공항, 하네다 공항).
 */
public enum Airport {

    NRT("나리타 국제공항", 35.7720, 140.3929),
    HND("하네다 공항", 35.5494, 139.7798);

    private final String label;
    private final double lat;
    private final double lng;

    Airport(String label, double lat, double lng) {
        this.label = label;
        this.lat = lat;
        this.lng = lng;
    }

    public String label() {
        return label;
    }

    /** 공항 ↔ 숙소 대표 시간 (지금은 도쿄 기준, AirportAccess) */
    public int transferMinutes() {
        return AirportAccess.tokyo(this).map(AirportAccess::minutes).orElse(60);
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
