package com.example.japantravelplanner.itinerary.zone;

import com.example.japantravelplanner.trip.region.City;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TripZoneCityTest {

    @Test
    void 도쿄는_예전_지역_7개_그대로이고_19개_도시_모두_지역이_있다() {
        assertThat(TripZone.inCity(City.TOKYO)).containsExactly(TripZone.ASAKUSA_UENO, TripZone.GINZA_TOKYO,
                TripZone.AKIHABARA_KANDA, TripZone.SHINJUKU, TripZone.SHIBUYA_HARAJUKU, TripZone.ROPPONGI_TOWER, TripZone.ODAIBA);
        for (City city : City.values()) {
            assertThat(TripZone.inCity(city)).as(city.name()).isNotEmpty();
        }
    }

    @Test
    void 다른_도시의_지역은_반경이_겹치지_않는다() {
        // 일정은 숙소 도시의 지역만 쓰지만, 관광지는 반경 안에서 가장 가까운 지역 하나에 배정된다
        TripZone[] all = TripZone.values();
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                if (all[i].getCity() == all[j].getCity()) continue;
                assertThat(all[i].distanceTo(all[j]))
                        .as(all[i] + " ↔ " + all[j]).isGreaterThan(all[i].getRadiusMeters() + all[j].getRadiusMeters());
            }
        }
    }

    @Test
    void 좌표는_반경_안의_가장_가까운_지역에_들어가고_반경_밖이면_빈_값() {
        assertThat(TripZone.nearest(34.6687, 135.5032)).contains(TripZone.NAMBA);          // 道頓堀
        assertThat(TripZone.nearest(34.9944, 135.7845)).contains(TripZone.GION);           // 清水寺
        assertThat(TripZone.nearest(35.5233, 138.7465)).contains(TripZone.LAKE_KAWAGUCHI); // 大石公園 (반경 4.5km)
        assertThat(TripZone.nearest(33.286, 131.4316)).isEmpty();                          // 別府ロープウェイ (지역 밖)
    }

    @Test
    void 멀리_떨어진_지역은_오다이바뿐이다() {
        assertThat(Arrays.stream(TripZone.values()).filter(TripZone::isRemote)).containsExactly(TripZone.ODAIBA);
    }

    @Test
    void 지역마다_별칭이_있고_한_도시_안에서는_별칭이_겹치지_않는다() {
        // 교토 기타노(덴만구)와 고베 기타노(이진칸)처럼 다른 도시는 같은 말이 있어도 된다 (일정은 숙소 도시 지역만 쓴다)
        for (City city : City.values()) {
            Set<String> seen = new HashSet<>();
            for (TripZone z : TripZone.inCity(city)) {
                assertThat(z.getAliases()).as(z.name()).isNotEmpty();
                for (String a : z.getAliases()) {
                    assertThat(seen.add(a)).as("겹친 별칭 " + a).isTrue();
                }
            }
        }
        assertThat(TripZone.ASAKUSA_UENO.getAliases()).contains("센소지");
    }

    @Test
    void 지역_중심은_그_도시_범위_안에_있다() {
        for (TripZone z : TripZone.values()) {
            assertThat(z.getCity().contains(z.getCenterLat(), z.getCenterLng())).as(z.name()).isTrue();
        }
    }
}
