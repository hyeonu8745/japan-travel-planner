package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 비를 피할 수 있는 곳인지 (10-16 비 오는 날 대안). 관광지 종류(Google types)로만 판단한다.
 * - INDOOR: 박물관·미술관, 백화점·쇼핑몰·상점, 수족관, 게임센터, 전망대(실내 전망실), 영화관 등
 * - OUTDOOR: 공원·정원, 동물원·놀이공원, 신사·절, 기념비·명소 등 (실내 종류가 섞여 있지 않을 때)
 * - UNKNOWN: 거리·시장·관광 명소처럼 둘 다 아니거나 섞인 곳 → 비 오는 날 대안에서 바꾸지 않는다 (원칙 6: 불확실하면 그대로)
 */
public enum Shelter {
    INDOOR, OUTDOOR, UNKNOWN;

    private static final Set<String> INDOOR_TYPES = Set.of(
            "museum", "art_museum", "art_gallery", "shopping_mall", "department_store", "store", "toy_store",
            "book_store", "electronics_store", "clothing_store", "aquarium", "amusement_center", "video_arcade",
            "observation_deck", "movie_theater", "planetarium", "library", "bowling_alley", "karaoke", "spa");

    private static final Set<String> OUTDOOR_TYPES = Set.of(
            "park", "garden", "botanical_garden", "national_park", "state_park", "city_park", "scenic_spot",
            "zoo", "amusement_park", "buddhist_temple", "shinto_shrine", "hindu_temple", "place_of_worship",
            "historical_landmark", "monument", "sculpture", "beach", "hiking_area", "plaza", "bridge", "cemetery");

    /** 실내 종류가 있어도 밖을 걷는 곳 (절 안의 박물관 표시 등) — 이것이 있으면 실내로 보지 않는다 */
    private static final Set<String> MAINLY_OUTDOOR = Set.of(
            "park", "garden", "botanical_garden", "national_park", "zoo", "amusement_park",
            "buddhist_temple", "shinto_shrine");

    public static Shelter of(Attraction attraction) {
        if (attraction == null || attraction.getTypes() == null) return UNKNOWN;
        Set<String> types = Arrays.stream(attraction.getTypes().split(","))
                .map(String::trim).collect(Collectors.toSet());
        boolean indoor = types.stream().anyMatch(INDOOR_TYPES::contains);
        boolean outdoor = types.stream().anyMatch(OUTDOOR_TYPES::contains);
        boolean mainlyOutdoor = types.stream().anyMatch(MAINLY_OUTDOOR::contains);
        if (indoor && !mainlyOutdoor) return INDOOR;
        if (outdoor && !indoor) return OUTDOOR;
        if (mainlyOutdoor) return OUTDOOR;
        return UNKNOWN;
    }
}
