package com.example.japantravelplanner.dataimport;

import java.util.List;

/**
 * 확장 데이터 파일(data/processed/clean_*.json)의 한 줄. 파일의 칸 이름 그대로 받는다 (모르는 칸은 무시).
 */
public final class ExpansionRows {

    /** 묶음 클래스라 만들지 않는다 */
    private ExpansionRows() {
    }

    /** clean_restaurants_tabelog.json: 도쿄는 캐글(source=kaggle), 다른 도시는 따로 받은 타베로그 데이터 */
    public record TabelogRow(
            String storeId, String name, String city, double lat, double lng, String geoLevel, boolean suspect,
            String address, Double rating, Integer reviewCount, List<String> genres,
            String budgetLunch, Integer budgetLunchMin, Integer budgetLunchMax,
            String budgetDinner, Integer budgetDinnerMin, Integer budgetDinnerMax,
            String businessHours, String holiday, String source, List<String> closedDays,
            boolean closedIrregular, boolean noRegularHoliday, boolean closedOnHolidays,
            String nearestStation, Integer nearestDistance, boolean reservationOnly, boolean smokingAllSeats,
            boolean koreanMenu, boolean hyakumeiten, String awardMedal, boolean standingBar) {
    }

    /** clean_restaurants_chain.json: OSM 브랜드 태그가 있는 체인점 지점 */
    public record ChainRow(
            String osmId, String name, String brand, String brandKo, String category, String genreKo,
            Integer budgetMin, Integer budgetMax, double lat, double lng, String city, String openingHours) {
    }

    /** clean_attractions.json: OSM + 위키데이터 관광지 */
    public record AttractionRow(
            String osmId, String name, String nameEn, String nameKo, String kind, double lat, double lng,
            String city, String wikidata, Integer popularity, String openingHours, String website, String fee,
            String historyNote, boolean nightView, NightRow nightInfo, boolean mustSee, OfficialRow officialInfo,
            PhotoRow photo) {
    }

    /** 야경 명소의 밤 정보 */
    public record NightRow(String kind, String hours, String fee, String closed, String url) {
    }

    /** 공식 사이트에서 확인한 운영 정보 */
    public record OfficialRow(String status, String reservation, String closedDays, String hours, String fee, String url) {
    }

    /** 위키미디어 사진과 표시 정보 */
    public record PhotoRow(String thumb, Integer width, Integer height, String credit, String license,
                           String licenseUrl, String artist, String source) {
    }

    /** clean_facilities.json: OSM 편의 시설 */
    public record FacilityRow(
            String osmId, String name, String nameJa, String nameEn, String nameKo, double lat, double lng,
            String operator, String openingHours, String fee, String wheelchair, String type, String city,
            String brand, String network, String ref, String level, String capacity, String parking,
            String maxstay, String charge, String bathType) {
    }
}
