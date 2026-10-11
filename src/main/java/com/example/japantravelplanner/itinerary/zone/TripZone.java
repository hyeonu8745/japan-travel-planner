package com.example.japantravelplanner.itinerary.zone;

import com.example.japantravelplanner.global.util.GeoUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * 하루 일정의 단위가 되는 동네 지역 7개.
 * 구(ward) 경계가 아니라 여행자가 하루에 묶어서 다니는 범위로 정의했다.
 * 중심 좌표는 관광지 119곳의 분포를 보고 조정 (아키하바라·간다는 구단시타 쪽을 포함하도록 서쪽으로 이동)
 */
@Getter
@RequiredArgsConstructor
public enum TripZone {

    ASAKUSA_UENO("아사쿠사·우에노", 35.7140, 139.7860),
    GINZA_TOKYO("긴자·도쿄역", 35.6765, 139.7660),
    AKIHABARA_KANDA("아키하바라·간다", 35.6950, 139.7620),
    SHINJUKU("신주쿠", 35.6890, 139.7040),
    SHIBUYA_HARAJUKU("시부야·하라주쿠", 35.6660, 139.7010),
    ROPPONGI_TOWER("롯폰기·도쿄타워", 35.6605, 139.7390),
    ODAIBA("오다이바", 35.6300, 139.7750);

    /** 어느 지역 중심에서도 이보다 멀면 지역 밖 (자동 일정 후보에서 제외) */
    public static final double MAX_DISTANCE_M = 3_000;

    private final String label;
    private final double centerLat;
    private final double centerLng;

    /** 가장 가까운 지역 (어느 중심에서도 3km를 넘으면 빈 값) */
    public static Optional<TripZone> nearest(double lat, double lng) {
        return Arrays.stream(values())
                .min(Comparator.comparingDouble(zone -> zone.distanceTo(lat, lng)))
                .filter(zone -> zone.distanceTo(lat, lng) <= MAX_DISTANCE_M);
    }

    /** 여러 지역을 화면·설명용 한 줄로 ("아사쿠사·우에노 + 아키하바라·간다") */
    public static String labelOf(java.util.Collection<TripZone> zones) {
        return zones.stream().map(TripZone::getLabel).collect(java.util.stream.Collectors.joining(" + "));
    }

    /** 이 지역 중심에서 다른 지역 중심까지의 거리(미터) */
    public double distanceTo(TripZone other) {
        return distanceTo(other.centerLat, other.centerLng);
    }

    /** 이 지역 중심에서 주어진 좌표까지의 거리(미터) */
    public double distanceTo(double lat, double lng) {
        return GeoUtils.distanceMeters(centerLat, centerLng, lat, lng);
    }
}