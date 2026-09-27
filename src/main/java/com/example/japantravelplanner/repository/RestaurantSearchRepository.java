package com.example.japantravelplanner.repository;

import com.example.japantravelplanner.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.entity.Restaurant;
import org.springframework.data.domain.Page;

public interface RestaurantSearchRepository {

    Page<Restaurant> search(RestaurantSearchCondition condition);
}