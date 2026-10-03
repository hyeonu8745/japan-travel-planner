package com.example.japantravelplanner.itinerary.zone;

import com.example.japantravelplanner.global.util.GeoUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    private static final double EARTH_RADIUS_M = 6_371_000;

    private final String label;
    private final double centerLat;
    private final double centerLng;

    /** 이 지역 중심에서 주어진 좌표까지의 거리(미터) */
    public double distanceTo(double lat, double lng) {
        return GeoUtils.distanceMeters(centerLat, centerLng, lat, lng);
    }
}