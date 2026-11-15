package com.example.japantravelplanner.attraction.repository;

import com.example.japantravelplanner.attraction.dto.AttractionSearchCondition;
import com.example.japantravelplanner.attraction.entity.Attraction;
import org.springframework.data.domain.Page;

/**
 * 관광지 조합 검색 (값이 들어온 조건만 WHERE 에 붙이는 동적 쿼리).
 */
public interface AttractionSearchRepository {

    Page<Attraction> search(AttractionSearchCondition condition);
}