package com.example.japantravelplanner.attraction.dto;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 관광지 검색 결과 한 줄. 위치 검색일 때만 거리(distanceMeters)가 있다.
 */
@Getter
@Builder
@JsonPropertyOrder({
        "id", "name", "distanceMeters", "ward", "types", "rating", "reviewCnt",
        "address", "latitude", "longitude", "googleMapsUrl"
})
public class AttractionResponseDto {

    private Long id;
    private String name;
    private Integer distanceMeters;   // 위치 검색일 때만 값이 있음
    private String ward;
    private List<String> types;
    private Double rating;
    private Integer reviewCnt;
    private String address;
    private Double latitude;
    private Double longitude;
    private String googleMapsUrl;     // 입장료·영업시간 등은 Google Maps에서 확인

    /** 관광지 → 검색 결과 한 줄 (종류는 쉼표로 나누고, Google 지도 검색 주소를 붙인다) */
    public static AttractionResponseDto from(Attraction attraction, Integer distanceMeters) {
        Double lat = null;
        Double lng = null;
        if (attraction.getLocation() != null) {
            lat = attraction.getLocation().getY();
            lng = attraction.getLocation().getX();
        }

        return AttractionResponseDto.builder()
                .id(attraction.getId())
                .name(attraction.getName())
                .distanceMeters(distanceMeters)
                .ward(attraction.getWard())
                .types(splitTypes(attraction.typesOrKind()))
                .rating(attraction.getRating())
                .reviewCnt(attraction.getReviewCnt())
                .address(attraction.getAddress())
                .latitude(lat)
                .longitude(lng)
                .googleMapsUrl(googleMapsUrl(attraction))
                .build();
    }

    /** "park,tourist_attraction" → ["park", "tourist_attraction"] */
    private static List<String> splitTypes(String types) {
        if (types == null || types.isBlank()) {
            return List.of();
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(type -> !type.isEmpty())
                .toList();
    }

    /** Google 지도에서 이 장소를 여는 주소 (place_id 가 없으면 null) */
    private static String googleMapsUrl(Attraction attraction) {
        if (attraction.getPlaceId() == null) {
            return null;
        }
        return "https://www.google.com/maps/search/?api=1"
                + "&query=" + URLEncoder.encode(attraction.getName(), StandardCharsets.UTF_8)
                + "&query_place_id=" + URLEncoder.encode(attraction.getPlaceId(), StandardCharsets.UTF_8);
    }
}