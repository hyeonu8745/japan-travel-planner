package com.example.japantravelplanner.dto;

import com.example.japantravelplanner.entity.Restaurant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
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

    public static RestaurantResponseDto from(Restaurant restaurant) {
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
                .build();
    }
}