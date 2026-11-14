package com.example.japantravelplanner.itinerary.zone;

import com.example.japantravelplanner.trip.region.City;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TripZoneCityTest {

    @Test
    void 지금은_도쿄_지역_7개만_있다() {
        assertThat(TripZone.inCity(City.TOKYO)).hasSize(7).containsExactly(TripZone.values());
        assertThat(TripZone.inCity(City.OSAKA)).isEmpty();
    }

    @Test
    void 멀리_떨어진_지역은_오다이바뿐이다() {
        assertThat(Arrays.stream(TripZone.values()).filter(TripZone::isRemote)).containsExactly(TripZone.ODAIBA);
    }

    @Test
    void 지역마다_별칭이_있고_별칭은_겹치지_않는다() {
        Set<String> seen = new HashSet<>();
        for (TripZone z : TripZone.values()) {
            assertThat(z.getAliases()).as(z.name()).isNotEmpty();
            for (String a : z.getAliases()) {
                assertThat(seen.add(a)).as("겹친 별칭 " + a).isTrue();
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
