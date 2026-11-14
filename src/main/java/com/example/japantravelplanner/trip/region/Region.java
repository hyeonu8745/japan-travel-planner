package com.example.japantravelplanner.trip.region;

/**
 * 여행 권역 (11-08 리팩터링, 확장 계획 0-16). 여행은 권역을 고르고, 날짜마다 그 권역의 도시를 배정한다
 * (오사카 숙소 + 교토·나라 당일치기). 공항도 권역 단위로 고른다.
 */
public enum Region {

    KANTO("수도권"),
    KANSAI("간사이"),
    CHUBU("중부"),
    HIROSHIMA("히로시마"),
    KYUSHU_NORTH("규슈 북부"),
    HOKKAIDO("홋카이도"),
    OKINAWA("오키나와");

    private final String label;

    Region(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
