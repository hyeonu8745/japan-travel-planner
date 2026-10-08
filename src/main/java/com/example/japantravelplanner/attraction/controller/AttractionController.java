package com.example.japantravelplanner.attraction.controller;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto;
import com.example.japantravelplanner.attraction.dto.AttractionResponseDto;
import com.example.japantravelplanner.attraction.dto.PhotoUriResponseDto;
import com.example.japantravelplanner.attraction.service.AttractionDetailService;
import com.example.japantravelplanner.attraction.dto.AttractionSearchCondition;
import com.example.japantravelplanner.attraction.service.AttractionService;
import com.example.japantravelplanner.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttractionController {

    private final AttractionService attractionService;
    private final AttractionDetailService attractionDetailService;

    @GetMapping("/api/attractions/search")
    public PageResponseDto<AttractionResponseDto> searchAttractions(AttractionSearchCondition condition) {
        return attractionService.search(condition);
    }

    /** 관광지 상세 (사진 목록·소개·영업시간·리뷰). Google에서 볼 때마다 받아온다. 로그인 필요 */
    @GetMapping("/api/attractions/{attractionId}/details")
    public AttractionDetailResponseDto details(@PathVariable Long attractionId) {
        return attractionDetailService.details(attractionId);
    }

    /** 상세에서 받은 사진 이름 → 화면에 띄울 사진 주소. 로그인 필요 */
    @GetMapping("/api/attractions/{attractionId}/photo")
    public PhotoUriResponseDto photo(@PathVariable Long attractionId,
                                     @RequestParam String name,
                                     @RequestParam(required = false) Integer maxWidthPx) {
        return attractionDetailService.photo(attractionId, name, maxWidthPx);
    }
}