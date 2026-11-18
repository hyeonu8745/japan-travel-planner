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

    @Test
    void 좌표가_들어가는_도시를_찾고_겹치면_중심이_가까운_쪽() {
        assertThat(City.containing(34.6687, 135.5032)).contains(City.OSAKA);    // 道頓堀
        assertThat(City.containing(35.7148, 139.7967)).contains(City.TOKYO);    // 浅草寺
        // 가와사키역: 도쿄(25km)·요코하마(15km) 범위가 겹치는 곳 → 중심이 더 가까운 쪽
        double kawasakiToTokyo = com.example.japantravelplanner.global.util.GeoUtils.distanceMeters(
                City.TOKYO.centerLat(), City.TOKYO.centerLng(), 35.5313, 139.6969);
        double kawasakiToYokohama = com.example.japantravelplanner.global.util.GeoUtils.distanceMeters(
                City.YOKOHAMA.centerLat(), City.YOKOHAMA.centerLng(), 35.5313, 139.6969);
        assertThat(City.containing(35.5313, 139.6969))
                .contains(kawasakiToTokyo < kawasakiToYokohama ? City.TOKYO : City.YOKOHAMA);
        assertThat(City.containing(37.5665, 126.9780)).isEmpty();             // 서울
    }

    @Test
    void 영어_이름은_첫_글자만_대문자() {
        assertThat(City.TOKYO.englishName()).isEqualTo("Tokyo");
        assertThat(City.KAWAGUCHIKO.englishName()).isEqualTo("Kawaguchiko");
    }

    @Test
    void 숙소_이름_주소의_도시_이름으로_짐작한다() {
        assertThat(City.guess("Hotel Gracery", "大阪府大阪市中央区難波")).contains(City.OSAKA);
        assertThat(City.guess("교토역 앞 호텔", null)).contains(City.KYOTO);
        assertThat(City.guess(null, "東京都新宿区西新宿")).contains(City.TOKYO);   // 「東京都」의 「京都」는 교토가 아니다
        assertThat(City.guess("博多グリーンホテル", null)).contains(City.FUKUOKA);
        assertThat(City.guess("우리나라 여행객 숙소", null)).isEmpty();             // "나라"만으로는 나라시가 아니다
        assertThat(City.guess("Hotel Gracery Shinjuku")).isEmpty();
    }
}
