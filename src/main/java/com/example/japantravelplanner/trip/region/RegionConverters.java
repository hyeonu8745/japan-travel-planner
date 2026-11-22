package com.example.japantravelplanner.trip.region;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 권역·도시를 DB에 이름 문자열로 저장한다. @Enumerated 는 Hibernate 가 허용 값 CHECK 제약을 만들어서,
 * 값을 더할 때마다 DB 제약도 고쳐야 한다 (안 고치면 저장이 실패한다. 공항과 같은 이유).
 */
public final class RegionConverters {

    /** 컨버터 두 개를 묶어 두는 클래스 (만들지 않는다) */
    private RegionConverters() {
    }

    @Converter
    public static class RegionConverter implements AttributeConverter<Region, String> {
        /** 권역 → DB 문자열 (이름) */
        @Override
        public String convertToDatabaseColumn(Region region) {
            return region == null ? null : region.name();
        }

        /** DB 문자열 → 권역 (비어 있으면 null) */
        @Override
        public Region convertToEntityAttribute(String value) {
            return value == null || value.isBlank() ? null : Region.valueOf(value);
        }
    }

    @Converter
    public static class CityConverter implements AttributeConverter<City, String> {
        /** 도시 → DB 문자열 (이름) */
        @Override
        public String convertToDatabaseColumn(City city) {
            return city == null ? null : city.name();
        }

        /** DB 문자열 → 도시 (비어 있으면 null) */
        @Override
        public City convertToEntityAttribute(String value) {
            return value == null || value.isBlank() ? null : City.valueOf(value);
        }
    }

    /** 도시 목록 → "KYOTO,NARA" (당일치기 도시, 11-22). 순서를 지킨다 */
    @Converter
    public static class CityListConverter implements AttributeConverter<java.util.List<City>, String> {
        /** 도시 목록 → DB 문자열 (비어 있으면 null) */
        @Override
        public String convertToDatabaseColumn(java.util.List<City> cities) {
            if (cities == null || cities.isEmpty()) return null;
            return String.join(",", cities.stream().map(Enum::name).toList());
        }

        /** DB 문자열 → 도시 목록 (모르는 이름은 건너뛴다) */
        @Override
        public java.util.List<City> convertToEntityAttribute(String value) {
            if (value == null || value.isBlank()) return new java.util.ArrayList<>();
            java.util.List<City> result = new java.util.ArrayList<>();
            for (String part : value.split(",")) City.of(part).ifPresent(result::add);
            return result;
        }
    }
}
