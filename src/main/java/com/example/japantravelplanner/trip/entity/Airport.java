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

    // 10-16: 2026-11-01부터 일본 면세가 리펀드 방식(출국 때 공항 세관에서 확인 후 환급)으로 바뀌어
    // 대도시 공항은 세관 줄이 길어진다 → 출발 3시간 전 공항 도착 (사용자 결정). 작은 공항은 확장 때 공항마다 정한다
    NRT("나리타 국제공항", 35.7720, 140.3929, 180),
    HND("하네다 공항", 35.5494, 139.7798, 180);

    private final String label;
    private final double lat;
    private final double lng;
    private final int beforeDepartureMinutes;

    Airport(String label, double lat, double lng, int beforeDepartureMinutes) {
        this.label = label;
        this.lat = lat;
        this.lng = lng;
        this.beforeDepartureMinutes = beforeDepartureMinutes;
    }

    /** 비행기 출발 몇 분 전에 공항에 도착할지 (국제선 수속 + 면세 환급 세관 확인) */
    public int beforeDepartureMinutes() {
        return beforeDepartureMinutes;
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
