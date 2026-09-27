package com.example.japantravelplanner.restaurant.service;

import com.example.japantravelplanner.restaurant.dto.RestaurantResponseDto;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.example.japantravelplanner.restaurant.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.restaurant.dto.RestaurantSearchResponseDto;
import com.example.japantravelplanner.global.util.GeoUtils;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public List<RestaurantResponseDto> findNearbyRestaurants(double lat, double lng, double radiusMeters) {
        List<Restaurant> restaurants = restaurantRepository.findWithinRadius(lat, lng, radiusMeters);

        return restaurants.stream()
                .map(RestaurantResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RestaurantSearchResponseDto search(RestaurantSearchCondition condition) {
        condition.validate();

        Page<Restaurant> result = restaurantRepository.search(condition);

        List<RestaurantResponseDto> content = result.getContent().stream()
                .map(restaurant -> RestaurantResponseDto.from(restaurant, distanceFrom(condition, restaurant)))
                .toList();

        return RestaurantSearchResponseDto.of(result, content);
    }

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