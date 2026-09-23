package com.example.japantravelplanner.service;

import com.example.japantravelplanner.dto.RestaurantResponseDto;
import com.example.japantravelplanner.entity.Restaurant;
import com.example.japantravelplanner.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
}