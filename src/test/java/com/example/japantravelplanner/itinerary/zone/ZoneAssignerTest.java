package com.example.japantravelplanner.itinerary.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneAssignerTest {

    private final ZoneAssigner assigner = new ZoneAssigner();

    @ParameterizedTest(name = "{0} → {3}")
    @CsvSource({
            // 이름,                  위도,       경도,        기대 지역
            "센소지,                  35.7147651, 139.7966553, ASAKUSA_UENO",
            "도쿄국립박물관,           35.7188351, 139.7765215, ASAKUSA_UENO",
            "야스쿠니 신사(구단시타),   35.6941,    139.7437,    AKIHABARA_KANDA",
            "신주쿠교엔,              35.6851763, 139.7100517, SHINJUKU",
            "SHIBUYA SKY,           35.6586719, 139.7019848, SHIBUYA_HARAJUKU",
            "도쿄타워,                35.6585805, 139.7454329, ROPPONGI_TOWER",
            "레인보우 브리지,          35.6365639, 139.7631443, ODAIBA",
            "쓰키시마 몬자 거리,       35.6640,    139.7820,    GINZA_TOKYO"
    })
    void 가장_가까운_지역에_배정한다(String name, double lat, double lng, TripZone expected) {
        assertThat(assigner.assign(lat, lng)).contains(expected);
    }

    @Test
    void 모든_지역_중심에서_3km를_넘으면_배정하지_않는다() {
        // 아쿠아파크 시나가와: 롯폰기·도쿄타워, 오다이바 중심에서 모두 3.5km 이상
        assertThat(assigner.assign(35.6285, 139.7356)).isEmpty();
    }

    @Test
    void 지역_중심은_자기_자신에게_배정된다() {
        for (TripZone zone : TripZone.values()) {
            assertThat(assigner.assign(zone.getCenterLat(), zone.getCenterLng())).contains(zone);
        }
    }
}