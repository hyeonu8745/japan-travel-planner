package com.example.japantravelplanner.itinerary.transit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 구간 하나의 대강의 대중교통 경로 (11-22). 정차역 순서로만 찾은 참고용이다 (시간표·환승 통로 시간은 없다).
 *
 * @param fromStation  타는 역 이름 (일본어), fromStationKo 한국어 이름(있으면)
 * @param walkToMeters 앞 장소 → 타는 역 직선거리
 * @param toStation    내리는 역, walkFromMeters 내리는 역 → 다음 장소 직선거리
 * @param segments     탈 노선 (1개 = 직통, 2개 = 한 번 갈아타기)
 */
public record TransitPlan(String fromStation, String fromStationKo, int walkToMeters,
                          String toStation, String toStationKo, int walkFromMeters,
                          List<Segment> segments) {

    /**
     * 노선 하나 타기
     * @param line   노선 이름 (예: 東京メトロ銀座線), mode subway·train·tram·monorail·light_rail·funicular·bus
     * @param colour 노선 색 (#RRGGBB, 없으면 null), stops 지나는 정거장 수
     */
    public record Segment(String line, String mode, String ref, String colour, String operator,
                          String from, String fromKo, String to, String toKo, int stops) {
    }

    /** 갈아타는 역 (직통이면 null). 응답 JSON 에도 나간다 */
    @JsonProperty("transferStation")
    public String transferStation() {
        return segments.size() > 1 ? segments.get(0).to() : null;
    }
}
