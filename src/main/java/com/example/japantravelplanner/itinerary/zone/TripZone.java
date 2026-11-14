package com.example.japantravelplanner.itinerary.zone;

import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.trip.region.City;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 하루 일정의 단위가 되는 동네 지역 7개.
 * 구(ward) 경계가 아니라 여행자가 하루에 묶어서 다니는 범위로 정의했다.
 * 중심 좌표는 관광지 119곳의 분포를 보고 조정 (아키하바라·간다는 구단시타 쪽을 포함하도록 서쪽으로 이동)
 *
 * 11-08 리팩터링: 지역마다 도시(City)를 붙였다 (권역 → 도시 → 지역). 지금은 도쿄 지역만 있고,
 * 11월 확장 때 다른 도시의 지역을 여기에 더한다. 대화에서 지역을 알아보는 별칭과
 * "멀어서 관심사가 없으면 점수 절반"(오다이바) 표시도 DayZonePlanner 에 박혀 있던 것을 지역으로 옮겼다.
 */
@Getter
@RequiredArgsConstructor
public enum TripZone {

    ASAKUSA_UENO("아사쿠사·우에노", 35.7140, 139.7860, City.TOKYO, false,
            List.of("아사쿠사", "우에노", "야나카", "센소지")),
    GINZA_TOKYO("긴자·도쿄역", 35.6765, 139.7660, City.TOKYO, false,
            List.of("긴자", "도쿄역", "마루노우치", "니혼바시", "쓰키지", "쓰키시마", "황궁")),
    AKIHABARA_KANDA("아키하바라·간다", 35.6950, 139.7620, City.TOKYO, false,
            List.of("아키하바라", "간다", "진보초", "구단시타", "야스쿠니",
                    "피규어", "프라모델", "건프라", "애니")),   // 피규어·애니 숍이 모인 곳 (Wish.HOBBY)
    SHINJUKU("신주쿠", 35.6890, 139.7040, City.TOKYO, false,
            List.of("신주쿠", "가부키초", "도청")),
    SHIBUYA_HARAJUKU("시부야·하라주쿠", 35.6660, 139.7010, City.TOKYO, false,
            List.of("시부야", "하라주쿠", "오모테산도", "메이지", "요요기")),
    ROPPONGI_TOWER("롯폰기·도쿄타워", 35.6605, 139.7390, City.TOKYO, false,
            List.of("롯폰기", "도쿄타워", "도쿄 타워", "아자부")),
    ODAIBA("오다이바", 35.6300, 139.7750, City.TOKYO, true,
            List.of("오다이바", "레인보우"));

    /** 어느 지역 중심에서도 이보다 멀면 지역 밖 (자동 일정 후보에서 제외) */
    public static final double MAX_DISTANCE_M = 3_000;

    private final String label;
    private final double centerLat;
    private final double centerLng;
    private final City city;
    /** 중심에서 떨어진 곳: 야경·쇼핑 관심사가 없으면 지역 점수를 절반으로 (오다이바) */
    private final boolean remote;
    /** 대화에서 이 지역을 알아보는 말 */
    private final List<String> aliases;

    /** 이 도시의 지역들 */
    public static List<TripZone> inCity(City city) {
        return Arrays.stream(values()).filter(zone -> zone.city == city).toList();
    }

    /** 가장 가까운 지역 (어느 중심에서도 3km를 넘으면 빈 값) */
    public static Optional<TripZone> nearest(double lat, double lng) {
        return Arrays.stream(values())
                .min(Comparator.comparingDouble(zone -> zone.distanceTo(lat, lng)))
                .filter(zone -> zone.distanceTo(lat, lng) <= MAX_DISTANCE_M);
    }

    /**
     * 그날 실제로 도는 지역: 관광지가 있는 지역만, 중심 지역에 관광지가 있으면 맨 앞에.
     * 관광지가 하나도 없으면(첫날 저녁만 있는 날 등) 중심 지역.
     * 10-11: 3일차 중심이 롯폰기였지만 롯폰기 관광지는 2일차가 다 써서 시부야만 돌았는데 "롯폰기 + 시부야"로 보였다
     * @param stopZones 관광지 지역 (방문 순서대로, 중복 가능)
     */
    public static java.util.List<TripZone> visited(TripZone primary, java.util.Collection<TripZone> stopZones) {
        java.util.LinkedHashSet<TripZone> zones = new java.util.LinkedHashSet<>(stopZones);
        zones.remove(null);
        if (zones.isEmpty()) return primary == null ? java.util.List.of() : java.util.List.of(primary);
        if (zones.remove(primary)) {
            java.util.List<TripZone> ordered = new java.util.ArrayList<>();
            ordered.add(primary);
            ordered.addAll(zones);
            return java.util.List.copyOf(ordered);
        }
        return java.util.List.copyOf(zones);
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