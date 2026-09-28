package com.example.japantravelplanner.global.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/** 모든 목록 API가 공통으로 쓰는 페이지 응답 */
@Getter
@JsonPropertyOrder({"totalElements", "totalPages", "page", "size", "hasNext", "content"})
public class PageResponseDto<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int page;
    private final int size;
    private final boolean hasNext;

    private PageResponseDto(Page<T> source) {
        this.content = source.getContent();
        this.totalElements = source.getTotalElements();
        this.totalPages = source.getTotalPages();
        this.page = source.getNumber();
        this.size = source.getSize();
        this.hasNext = source.hasNext();
    }

    public static <T> PageResponseDto<T> from(Page<T> source) {
        return new PageResponseDto<>(source);
    }
}