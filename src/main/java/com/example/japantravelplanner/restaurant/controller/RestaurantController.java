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

/**
 * 식당 API. 검색은 로그인 없이, 상세·사진은 Google 요금이 들어서 로그인한 사용자만 (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final RestaurantDetailService restaurantDetailService;

    /** 한 지점 반경 안의 식당 (로그인 없이) */
    @GetMapping("/api/restaurants/nearby")
    public List<RestaurantResponseDto> getNearbyRestaurants(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radius
    ) {
        return restaurantService.findNearbyRestaurants(lat, lng, radius);
    }

    /** 식당 조합 검색 (장르·최소 평점·리뷰 수·끼니 예산·위치 반경, 정렬, 페이지). 로그인 없이 */
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