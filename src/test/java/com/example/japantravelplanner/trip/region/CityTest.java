package com.example.japantravelplanner.trip.region;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CityTest {

    @Test
    void 도시는_19개이고_모든_권역에_도시가_있다() {
        assertThat(City.values()).hasSize(19);
        for (Region r : Region.values()) {
            assertThat(City.inRegion(r)).as(r.name()).isNotEmpty();
        }
        assertThat(City.inRegion(Region.KANSAI)).containsExactly(City.OSAKA, City.KYOTO, City.KOBE, City.NARA);
        assertThat(Arrays.stream(City.values()).mapToInt(c -> City.inRegion(c.region()).contains(c) ? 1 : 0).sum())
                .isEqualTo(19);
    }

    @Test
    void 이름으로_찾을_때_대소문자와_공백을_무시하고_모르면_empty() {
        assertThat(City.of(" kyoto ")).contains(City.KYOTO);
        assertThat(City.of("TOKYO")).contains(City.TOKYO);
        assertThat(City.of("SEOUL")).isEmpty();
        assertThat(City.of("")).isEmpty();
        assertThat(City.of(null)).isEmpty();
    }

    @Test
    void 도시_범위는_중심에서_반경_안이다() {
        // 센소지는 도쿄 안, 요코하마 중심은 도쿄 반경(25km) 밖
        assertThat(City.TOKYO.contains(35.7148, 139.7967)).isTrue();
        assertThat(City.TOKYO.contains(35.45, 139.63)).isFalse();
        assertThat(City.YOKOHAMA.contains(35.45, 139.63)).isTrue();
        // 기요미즈데라는 교토 안, 오사카 밖
        assertThat(City.KYOTO.contains(34.9949, 135.7850)).isTrue();
        assertThat(City.OSAKA.contains(34.9949, 135.7850)).isFalse();
    }

    @Test
    void 이름표() {
        assertThat(City.TOKYO.label()).isEqualTo("도쿄");
        assertThat(Region.KANTO.label()).isEqualTo("수도권");
    }

    @Test
    void DB_변환은_이름_문자열이고_null은_null() {
        var rc = new RegionConverters.RegionConverter();
        var cc = new RegionConverters.CityConverter();
        assertThat(rc.convertToDatabaseColumn(Region.KANSAI)).isEqualTo("KANSAI");
        assertThat(rc.convertToEntityAttribute("KANSAI")).isEqualTo(Region.KANSAI);
        assertThat(cc.convertToDatabaseColumn(City.KOBE)).isEqualTo("KOBE");
        assertThat(cc.convertToEntityAttribute("KOBE")).isEqualTo(City.KOBE);
        assertThat(cc.convertToDatabaseColumn(null)).isNull();
        assertThat(cc.convertToEntityAttribute(null)).isNull();
    }
}
