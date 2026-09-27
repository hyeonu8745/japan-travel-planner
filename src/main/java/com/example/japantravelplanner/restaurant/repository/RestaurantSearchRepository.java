package com.example.japantravelplanner.restaurant.repository;

import com.example.japantravelplanner.restaurant.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.springframework.data.domain.Page;

public interface RestaurantSearchRepository {

    Page<Restaurant> search(RestaurantSearchCondition condition);
}