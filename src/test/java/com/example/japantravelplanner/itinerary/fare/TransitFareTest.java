package com.example.japantravelplanner.itinerary.fare;

import com.example.japantravelplanner.trip.region.City;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 거리별 지하철 운임과 1일권 (10-16) */
class TransitFareTest {

    private final FareProperties props = new FareProperties();

    @Test
    void 거리는_km_올림으로_구간을_찾고_표보다_길면_마지막_구간() {
        assertThat(TransitFare.fare(props.getMetroBands(), 800)).isEqualTo(178);
        assertThat(TransitFare.fare(props.getMetroBands(), 6000)).isEqualTo(178);
        assertThat(TransitFare.fare(props.getMetroBands(), 6001)).isEqualTo(209);   // 7km
        assertThat(TransitFare.fare(props.getToeiBands(), 6001)).isEqualTo(220);    // 도에이가 더 비싸다
        assertThat(TransitFare.fare(props.getMetroBands(), 55_000)).isEqualTo(324);
    }

    @Test
    void 사흘_내내_많이_타면_72시간권() {
        TransitFare.PassPlan plan = TransitFare.bestPasses(List.of(700, 1200, 900), props);

        assertThat(plan.passes()).containsExactly(new TransitFare.Pass(1, 3, 72, 2000));
        assertThat(plan.cost()).isEqualTo(2000);
        assertThat(plan.payAsYouGo()).isEqualTo(2800);
        assertThat(plan.savings()).isEqualTo(800);
    }

    @Test
    void 조금_타면_1일권을_쓰지_않고_안_타는_날은_1일권으로_덮지_않는다() {
        assertThat(TransitFare.bestPasses(List.of(400, 400), props).passes()).isEmpty();

        // 첫날·셋째 날만 많이 타면 24시간권 두 장 (가운데 날은 렌터카·도보만)
        TransitFare.PassPlan plan = TransitFare.bestPasses(List.of(1100, 0, 1100), props);
        assertThat(plan.passes()).containsExactly(new TransitFare.Pass(1, 1, 24, 1000), new TransitFare.Pass(3, 3, 24, 1000));
        assertThat(plan.cost()).isEqualTo(2000);
    }

    @Test
    void 이틀_많이_타고_하루_조금이면_48시간권과_그날_요금() {
        TransitFare.PassPlan plan = TransitFare.bestPasses(List.of(900, 900, 300), props);

        assertThat(plan.passes()).containsExactly(new TransitFare.Pass(1, 2, 48, 1500));
        assertThat(plan.cost()).isEqualTo(1800);
    }

    @Test
    void 지하철_운임은_도쿄만_있고_도쿄_값은_설정_그대로다() {
        FareProperties props = new FareProperties();
        FareProperties.CityFare tokyo = props.forCity(City.TOKYO).orElseThrow();
        assertThat(tokyo.minBands()).isSameAs(props.getMetroBands());
        assertThat(tokyo.maxBands()).isSameAs(props.getToeiBands());
        assertThat(tokyo.pass72Yen()).isEqualTo(props.getPass72Yen());
        assertThat(props.forCity(City.OSAKA)).isEmpty();
    }
}
