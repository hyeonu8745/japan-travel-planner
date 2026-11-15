package com.example.japantravelplanner.restaurant.service;

import com.example.japantravelplanner.restaurant.dto.RestaurantResponseDto;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.example.japantravelplanner.restaurant.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.global.util.GeoUtils;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import com.example.japantravelplanner.global.dto.PageResponseDto;

/**
 * 식당 주변 검색·조합 검색 (우리 DB 만, 외부 호출 없음).
 */
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    /** 한 지점 반경 안의 식당 */
    public List<RestaurantResponseDto> findNearbyRestaurants(double lat, double lng, double radiusMeters) {
        List<Restaurant> restaurants = restaurantRepository.findWithinRadius(lat, lng, radiusMeters);

        return restaurants.stream()
                .map(RestaurantResponseDto::from)
                .collect(Collectors.toList());
    }

    /** 조건 검사 후 조합 검색, 위치 검색이면 거리를 붙인다 */
    @Transactional(readOnly = true)
    public PageResponseDto<RestaurantResponseDto> search(RestaurantSearchCondition condition) {
        condition.validate();

        Page<RestaurantResponseDto> result = restaurantRepository.search(condition)
                .map(restaurant -> RestaurantResponseDto.from(restaurant, distanceFrom(condition, restaurant)));

        return PageResponseDto.from(result);
    }

    /** 검색 위치에서 식당까지 직선 거리(m). 위치 조건이 없으면 null */
    private Integer distanceFrom(RestaurantSearchCondition condition, Restaurant restaurant) {
        if (!condition.hasLocation() || restaurant.getLocation() == null) {
            return null;
        }
        double meters = GeoUtils.distanceMeters(
                condition.getLat(), condition.getLng(),
                restaurant.getLocation().getY(), restaurant.getLocation().getX());
        return (int) Math.round(meters);
    }
}