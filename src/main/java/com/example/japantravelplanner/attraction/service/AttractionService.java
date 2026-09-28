package com.example.japantravelplanner.attraction.service;

import com.example.japantravelplanner.attraction.dto.AttractionResponseDto;
import com.example.japantravelplanner.attraction.dto.AttractionSearchCondition;
import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.dto.PageResponseDto;
import com.example.japantravelplanner.global.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttractionService {

    private final AttractionRepository attractionRepository;

    @Transactional(readOnly = true)
    public PageResponseDto<AttractionResponseDto> search(AttractionSearchCondition condition) {
        condition.validate();

        Page<AttractionResponseDto> result = attractionRepository.search(condition)
                .map(attraction -> AttractionResponseDto.from(attraction, distanceFrom(condition, attraction)));

        return PageResponseDto.from(result);
    }

    private Integer distanceFrom(AttractionSearchCondition condition, Attraction attraction) {
        if (!condition.hasLocation() || attraction.getLocation() == null) {
            return null;
        }
        double meters = GeoUtils.distanceMeters(
                condition.getLat(), condition.getLng(),
                attraction.getLocation().getY(), attraction.getLocation().getX());
        return (int) Math.round(meters);
    }
}