package com.example.japantravelplanner.restaurant.controller;

import com.example.japantravelplanner.attraction.dto.PhotoUriResponseDto;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto;
import com.example.japantravelplanner.restaurant.dto.RestaurantResponseDto;
import com.example.japantravelplanner.restaurant.service.RestaurantDetailService;
import com.example.japantravelplanner.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.japantravelplanner.restaurant.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.global.dto.PageResponseDto;


import java.util.List;

@RestController
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final RestaurantDetailService restaurantDetailService;

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

    /** 식당 상세: 타베로그 정보 + Google 정보(사진·리뷰·영업시간). 로그인 필요 (Google 요금) */
    @GetMapping("/api/restaurants/{restaurantId}/details")
    public RestaurantDetailResponseDto details(@PathVariable Long restaurantId) {
        return restaurantDetailService.details(restaurantId);
    }

    /** 상세의 사진 이름 → 화면에 띄울 사진 주소. 로그인 필요 */
    @GetMapping("/api/restaurants/{restaurantId}/photo")
    public PhotoUriResponseDto photo(@PathVariable Long restaurantId,
                                     @RequestParam String name,
                                     @RequestParam(required = false) Integer maxWidthPx) {
        return restaurantDetailService.photo(restaurantId, name, maxWidthPx);
    }
}