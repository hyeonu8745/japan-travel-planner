package com.example.japantravelplanner.trip.region;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 권역·도시를 DB에 이름 문자열로 저장한다 (11-08). @Enumerated 는 Hibernate 가 허용 값 CHECK 제약을 만들어서,
 * 값을 더할 때마다 DB 제약도 고쳐야 한다 (공항과 같은 이유, 10-06 되돌리기 409 사고).
 */
public final class RegionConverters {

    private RegionConverters() {
    }

    @Converter
    public static class RegionConverter implements AttributeConverter<Region, String> {
        @Override
        public String convertToDatabaseColumn(Region region) {
            return region == null ? null : region.name();
        }

        @Override
        public Region convertToEntityAttribute(String value) {
            return value == null || value.isBlank() ? null : Region.valueOf(value);
        }
    }

    @Converter
    public static class CityConverter implements AttributeConverter<City, String> {
        @Override
        public String convertToDatabaseColumn(City city) {
            return city == null ? null : city.name();
        }

        @Override
        public City convertToEntityAttribute(String value) {
            return value == null || value.isBlank() ? null : City.valueOf(value);
        }
    }
}
