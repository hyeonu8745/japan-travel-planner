package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 하루 관광지를 섞을 때 쓰는 큰 종류. Google type이 여러 개면 위에서 먼저 맞는 것 (StayTimes와 같은 순서).
 * 예: 浜離宮恩賜庭園(정원)과 그 안의 三百年の松(경승지)는 둘 다 NATURE라 같은 날 둘 다 고르면 감점된다.
 */
public enum PlaceKind {
    OBSERVATORY(Set.of("observation_deck")),
    AMUSEMENT(Set.of("amusement_park", "amusement_center", "zoo", "aquarium")),
    MUSEUM(Set.of("museum", "art_museum", "art_gallery")),
    WORSHIP(Set.of("buddhist_temple", "shinto_shrine", "hindu_temple", "place_of_worship", "mosque", "church")),
    NATURE(Set.of("park", "garden", "botanical_garden", "national_park", "state_park", "scenic_spot", "city_park")),
    SHOPPING(Set.of("shopping_mall", "market", "department_store", "store")),
    HISTORIC(Set.of("historical_landmark", "historical_place", "monument", "sculpture")),
    OTHER(Set.of());

    private static final List<PlaceKind> ORDER = List.of(values());

    private final Set<String> types;

    PlaceKind(Set<String> types) {
        this.types = types;
    }

    public static PlaceKind of(Attraction attraction) {
        if (attraction == null || attraction.getTypes() == null) return OTHER;
        Set<String> types = Arrays.stream(attraction.getTypes().split(","))
                .map(String::trim).collect(Collectors.toSet());
        for (PlaceKind kind : ORDER) {
            if (kind.types.stream().anyMatch(types::contains)) return kind;
        }
        return OTHER;
    }
}
