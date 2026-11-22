package com.example.japantravelplanner.trip.region;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 당일치기 도시 목록 ↔ DB 글자 (11-22) */
class RegionConvertersTest {

    private final RegionConverters.CityListConverter converter = new RegionConverters.CityListConverter();

    @Test
    void 도시_목록은_순서를_지켜_쉼표로_저장하고_모르는_이름은_건너뛴다() {
        assertThat(converter.convertToDatabaseColumn(List.of(City.KYOTO, City.NARA))).isEqualTo("KYOTO,NARA");
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();

        assertThat(converter.convertToEntityAttribute("KYOTO,NARA")).containsExactly(City.KYOTO, City.NARA);
        assertThat(converter.convertToEntityAttribute("KYOTO,SENDAI")).containsExactly(City.KYOTO);
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }
}
