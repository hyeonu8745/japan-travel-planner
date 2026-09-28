package com.example.japantravelplanner.restaurant.controller;

import com.example.japantravelplanner.restaurant.dto.RestaurantResponseDto;
import com.example.japantravelplanner.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.japantravelplanner.restaurant.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.global.dto.PageResponseDto;


import java.util.List;

@RestController
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping("/api/restaurants/nearby")
    public List<RestaurantResponseDto> getNearbyRestaurants(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radius
    ) {
        return restaurantService.findNearbyRestaurants(lat, lng, radius);
    }

    @GetMapping("/api/restaurants/search")
    public PageResponseDto<RestaurantResponseDto> searchRestaurants(RestaurantSearchCondition condition) {
        return restaurantService.search(condition);
    }
}