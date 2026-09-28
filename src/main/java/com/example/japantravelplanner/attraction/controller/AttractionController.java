package com.example.japantravelplanner.attraction.controller;

import com.example.japantravelplanner.attraction.dto.AttractionResponseDto;
import com.example.japantravelplanner.attraction.dto.AttractionSearchCondition;
import com.example.japantravelplanner.attraction.service.AttractionService;
import com.example.japantravelplanner.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttractionController {

    private final AttractionService attractionService;

    @GetMapping("/api/attractions/search")
    public PageResponseDto<AttractionResponseDto> searchAttractions(AttractionSearchCondition condition) {
        return attractionService.search(condition);
    }
}