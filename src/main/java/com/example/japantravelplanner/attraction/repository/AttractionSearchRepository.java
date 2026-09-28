package com.example.japantravelplanner.attraction.repository;

import com.example.japantravelplanner.attraction.dto.AttractionSearchCondition;
import com.example.japantravelplanner.attraction.entity.Attraction;
import org.springframework.data.domain.Page;

public interface AttractionSearchRepository {

    Page<Attraction> search(AttractionSearchCondition condition);
}