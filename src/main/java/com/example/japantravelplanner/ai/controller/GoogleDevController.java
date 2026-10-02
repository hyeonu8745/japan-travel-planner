package com.example.japantravelplanner.ai.controller;

import com.example.japantravelplanner.global.google.GoogleGeocodingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Google API 연결 확인용 임시 API. 일정 생성이 완성되면 삭제한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/google")
public class GoogleDevController {

    private final GoogleGeocodingClient geocodingClient;

    @GetMapping("/geocode")
    public Object geocode(@RequestParam String address) {
        return geocodingClient.geocode(address)
                .map(r -> (Object) r)
                .orElse("결과 없음");
    }
}