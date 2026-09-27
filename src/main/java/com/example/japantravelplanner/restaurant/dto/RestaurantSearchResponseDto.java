package com.example.japantravelplanner.restaurant.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class RestaurantSearchResponseDto {

    private List<RestaurantResponseDto> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    private boolean hasNext;

    public static RestaurantSearchResponseDto of(Page<?> page, List<RestaurantResponseDto> content) {
        return RestaurantSearchResponseDto.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(page.getNumber())
                .size(page.getSize())
                .hasNext(page.hasNext())
                .build();
    }
}