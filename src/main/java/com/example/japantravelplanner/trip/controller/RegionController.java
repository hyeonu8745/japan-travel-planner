package com.example.japantravelplanner.trip.controller;

import com.example.japantravelplanner.trip.dto.RegionResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 여행 조건 화면용 권역·공항·도시·당일치기 목록 (11-22). 값은 코드에 있는 표라 DB를 읽지 않는다 */
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    /** 권역 7개와 공항·도시·당일치기 도시 */
    @GetMapping
    public List<RegionResponseDto> regions() {
        return RegionResponseDto.all();
    }
}
