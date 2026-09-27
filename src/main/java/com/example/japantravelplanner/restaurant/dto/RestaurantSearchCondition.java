package com.example.japantravelplanner.restaurant.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 레스토랑 조합 검색 조건.
 * 모든 조건은 선택이며, 값이 들어온 조건만 WHERE절에 추가된다.
 */
@Getter
@Setter
public class RestaurantSearchCondition {

    private static final double MAX_RADIUS = 5000;
    private static final int MAX_SIZE = 100;

    // 위치 (lat, lng는 함께 보내야 함)
    private Double lat;
    private Double lng;
    private Double radius = 1000.0;   // 미터, 기본 1km

    // 필터
    private String genre;
    private Double minRating;
    private Integer maxBudget;        // 엔 단위, 상한(max) 기준 엄격 비교
    private MealType mealType = MealType.DINNER;

    // 정렬, 페이징
    private RestaurantSortType sort = RestaurantSortType.RATING;
    private int page = 0;
    private int size = 20;

    public boolean hasLocation() {
        return lat != null && lng != null;
    }

    /** 조건 조합이 올바른지 검사한다. 잘못되면 IllegalArgumentException. */
    public void validate() {
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("lat과 lng는 함께 보내야 합니다.");
        }
        if (hasLocation() && (radius == null || radius <= 0 || radius > MAX_RADIUS)) {
            throw new IllegalArgumentException("radius는 0 초과 " + (int) MAX_RADIUS + " 이하여야 합니다.");
        }
        if (sort == RestaurantSortType.DISTANCE && !hasLocation()) {
            throw new IllegalArgumentException("거리순 정렬은 lat, lng가 필요합니다.");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
    }
}