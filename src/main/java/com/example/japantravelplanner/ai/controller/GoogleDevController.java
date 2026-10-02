package com.example.japantravelplanner.ai.controller;

import com.example.japantravelplanner.global.google.GoogleGeocodingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.japantravelplanner.itinerary.travel.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Google API 연결 확인용 임시 API. 일정 생성이 완성되면 삭제한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/google")
public class GoogleDevController {

    private final GoogleGeocodingClient geocodingClient;
    private final TravelTimeService travelTimeService;


    @GetMapping("/geocode")
    public Object geocode(@RequestParam String address) {
        return geocodingClient.geocode(address)
                .map(r -> (Object) r)
                .orElse("결과 없음");
    }

    /** 신주쿠 숙소 + 관광지 3곳의 이동 시간 표 (내일 오전 10시 출발 기준) */
    @GetMapping("/matrix")
    public Object matrix(@RequestParam(defaultValue = "TRANSIT") LegMode mode) {
        List<TravelPoint> points = List.of(
                new TravelPoint("숙소(가부키초)", 35.6951588, 139.7020539),
                new TravelPoint("신주쿠교엔", 35.6851763, 139.7100517),
                new TravelPoint("도청 전망대", 35.689733, 139.6916563),
                new TravelPoint("신주쿠 동쪽 출구 고양이", 35.6926516, 139.700803));

        Instant departure = LocalDate.now(ZoneId.of("Asia/Tokyo")).plusDays(1)
                .atTime(10, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant();

        TravelMatrix matrix = travelTimeService.buildMatrix(points, mode, departure);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            for (int j = 0; j < points.size(); j++) {
                if (i == j) continue;
                TravelLeg leg = matrix.get(i, j);
                rows.add(Map.of(
                        "from", points.get(i).name(),
                        "to", points.get(j).name(),
                        "minutes", leg.minutes(),
                        "mode", leg.mode(),
                        "estimated", leg.estimated()));
            }
        }
        return rows;
    }
}