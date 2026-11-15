package com.example.japantravelplanner.restaurant.repository;

import com.example.japantravelplanner.restaurant.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.springframework.data.domain.Page;

/**
 * 식당 조합 검색 (값이 들어온 조건만 WHERE 에 붙이는 동적 쿼리).
 */
public interface RestaurantSearchRepository {

    Page<Restaurant> search(RestaurantSearchCondition condition);
}