package com.example.japantravelplanner.attraction.dto;

import com.example.japantravelplanner.global.exception.BadRequestException;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 관광지 조합 검색 조건.
 * 모든 조건은 선택이며, 값이 들어온 조건만 WHERE절에 추가된다.
 */
@Getter
@Setter
public class AttractionSearchCondition {

    private static final double MAX_RADIUS = 5000;
    private static final int MAX_SIZE = 100;
    private static final int MAX_TYPES = 10;

    // 위치 (lat, lng는 함께 보내야 함)
    private Double lat;
    private Double lng;
    private Double radius = 1000.0;

    // 필터
    private String ward;            // 千代田区, 港区 등 (정확 일치)
    private List<String> types;     // museum, park 등 (하나라도 일치하면 포함)
    private Double minRating;
    private Integer minReviews;     // 최소 리뷰 수

    // 정렬, 페이징
    private AttractionSortType sort = AttractionSortType.RATING;
    private int page = 0;
    private int size = 20;

    /** 위치 조건(위도·경도)이 있는지 */
    public boolean hasLocation() {
        return lat != null && lng != null;
    }

    /** 종류 조건이 있는지 */
    public boolean hasTypes() {
        return types != null && !types.isEmpty();
    }

    /** 조건 값 검사 (리뷰 수, 위도·경도 짝, 반경, 거리순은 위치 필요, 종류 개수, 페이지 크기). 틀리면 400 */
    public void validate() {
        if (minReviews != null && minReviews < 0) {
            throw new BadRequestException("minReviews는 0 이상이어야 합니다.");
        }
        if ((lat == null) != (lng == null)) {
            throw new BadRequestException("lat과 lng는 함께 보내야 합니다.");
        }
        if (hasLocation() && (radius == null || radius <= 0 || radius > MAX_RADIUS)) {
            throw new BadRequestException("radius는 0 초과 " + (int) MAX_RADIUS + " 이하여야 합니다.");
        }
        if (sort == AttractionSortType.DISTANCE && !hasLocation()) {
            throw new BadRequestException("거리순 정렬은 lat, lng가 필요합니다.");
        }
        if (hasTypes() && types.size() > MAX_TYPES) {
            throw new BadRequestException("types는 최대 " + MAX_TYPES + "개까지 지정할 수 있습니다.");
        }
        if (page < 0) {
            throw new BadRequestException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
    }
}