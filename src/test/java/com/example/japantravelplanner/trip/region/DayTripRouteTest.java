package com.example.japantravelplanner.trip.region;

import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 숙소 도시 ↔ 당일치기 도시 경로 표 (11-22 확장 5단계) */
class DayTripRouteTest {

    @Test
    void 두_도시_사이_경로는_방향과_상관없이_같은_값이다() {
        DayTripRoute go = DayTripRoute.between(City.OSAKA, City.KYOTO).orElseThrow();
        DayTripRoute back = DayTripRoute.between(City.KYOTO, City.OSAKA).orElseThrow();

        assertThat(go).isSameAs(back);
        assertThat(go.minutes()).isEqualTo(28);
        assertThat(go.fareYen()).isEqualTo(580);
        assertThat(go.stationOf(City.OSAKA).name()).isEqualTo("오사카역");
        assertThat(go.stationOf(City.KYOTO).name()).isEqualTo("교토역");
        assertThat(go.other(City.OSAKA)).isEqualTo(City.KYOTO);
    }

    @Test
    void 경로가_없거나_같은_도시면_빈_값() {
        assertThat(DayTripRoute.between(City.OSAKA, City.SAPPORO)).isEmpty();
        assertThat(DayTripRoute.between(City.OSAKA, City.OSAKA)).isEmpty();
        assertThat(DayTripRoute.between(null, City.KYOTO)).isEmpty();
        assertThat(DayTripRoute.targets(City.NAHA)).isEmpty();
    }

    @Test
    void 그_도시에서_갈_수_있는_도시는_가까운_순() {
        assertThat(DayTripRoute.targets(City.OSAKA)).containsExactly(City.KOBE, City.KYOTO, City.NARA);
        assertThat(DayTripRoute.targets(City.TOKYO))
                .containsExactly(City.YOKOHAMA, City.KAMAKURA, City.HAKONE, City.KAWAGUCHIKO);
        assertThat(DayTripRoute.targets(City.SAPPORO)).containsExactly(City.OTARU);
    }

    @Test
    void 모든_경로는_같은_권역_안이고_역은_그_도시_범위와_지역_안에_있다() {
        for (DayTripRoute r : DayTripRoute.values()) {
            List<City> ends = java.util.Arrays.stream(City.values())
                    .filter(c -> DayTripRoute.between(c, r.other(c)).orElse(null) == r).toList();
            for (City c : ends) {
                DayTripRoute.Station s = r.stationOf(c);
                assertThat(c.contains(s.lat(), s.lng())).as(r + " " + s.name()).isTrue();
                assertThat(TripZone.nearest(s.lat(), s.lng())).as(r + " " + s.name()).isPresent();
                assertThat(r.other(c).region()).as(r.name()).isEqualTo(c.region());
            }
            assertThat(ends).as(r.name()).hasSize(2);
            assertThat(r.minutes()).isBetween(10, 180);
            assertThat(r.fareYen()).isPositive();
        }
    }
}
