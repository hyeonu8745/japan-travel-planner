package com.example.japantravelplanner.restaurant.dto;

import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;   // ← 추가
import lombok.Builder;
import lombok.Getter;

/**
 * 식당 검색 결과 한 줄. 위치 검색일 때만 거리(distanceMeters)가 있다.
 */
@Getter
@Builder
@JsonPropertyOrder({                                         // ← 추가 (여기부터)
        "id", "name", "distanceMeters", "genre", "ratingVal", "reviewCnt",
        "budgetLunch", "budgetDinner", "nearestStation", "address", "latitude", "longitude"
})
public class RestaurantResponseDto {

    private Long id;
    private String name;
    private String genre;
    private Double ratingVal;
    private Integer reviewCnt;
    private String budgetDinner;
    private String budgetLunch;
    private String address;
    private String nearestStation;
    private Double latitude;
    private Double longitude;
    private Integer distanceMeters; // 위치 검색일 때만 값이 있음

    /** 식당 → 검색 결과 한 줄 (거리 없음) */
    public static RestaurantResponseDto from(Restaurant restaurant) {
        return from(restaurant, null);
    }

    /** 식당 → 검색 결과 한 줄 (위치 검색이면 거리 포함) */
    public static RestaurantResponseDto from(Restaurant restaurant, Integer distanceMeters) {
        Double lat = null;
        Double lng = null;

        if (restaurant.getLocation() != null) {
            lat = restaurant.getLocation().getY(); // Y = 위도
            lng = restaurant.getLocation().getX(); // X = 경도
        }

        return RestaurantResponseDto.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .genre(restaurant.getGenre())
                .ratingVal(restaurant.getRatingVal())
                .reviewCnt(restaurant.getReviewCnt())
                .budgetDinner(restaurant.getBudgetDinner())
                .budgetLunch(restaurant.getBudgetLunch())
                .address(restaurant.getAddress())
                .nearestStation(restaurant.getNearestStation())
                .latitude(lat)
                .longitude(lng)
                .distanceMeters(distanceMeters)
                .build();
    }
}