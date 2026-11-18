package com.example.japantravelplanner.itinerary.fare;

import com.example.japantravelplanner.trip.region.City;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** application.yaml 의 도시별 운임(app.fare.cities)이 실제로 읽히고, 도시마다 계산할 수 있는지 (11-18) */
class FareCitiesTest {

    private static FareProperties load() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yaml"))
                .forEach(env.getPropertySources()::addLast);
        return Binder.get(env).bind("app.fare", FareProperties.class).get();
    }

    @Test
    void 열아홉_도시_모두_운임이_있다() throws Exception {
        FareProperties props = load();
        for (City city : City.values()) {
            assertThat(props.forCity(city)).as(city.name()).isPresent();
        }
    }

    @Test
    void 오사카_메트로_거리별_운임과_1일권() throws Exception {
        FareProperties.CityFare osaka = load().forCity(City.OSAKA).orElseThrow();

        assertThat(TransitFare.fare(osaka.minBands(), 2_500)).isEqualTo(190);
        assertThat(TransitFare.fare(osaka.minBands(), 6_200)).isEqualTo(240);
        assertThat(TransitFare.fare(osaka.maxBands(), 25_000)).isEqualTo(390);
        assertThat(osaka.pass24Yen()).isEqualTo(820);
        assertThat(osaka.passName()).startsWith("엔조이 에코 카드");
    }

    @Test
    void 교토는_버스가_최소_지하철이_최대() throws Exception {
        FareProperties.CityFare kyoto = load().forCity(City.KYOTO).orElseThrow();

        assertThat(TransitFare.fare(kyoto.minBands(), 5_000)).isEqualTo(230);
        assertThat(TransitFare.fare(kyoto.maxBands(), 5_000)).isEqualTo(260);
        // 같은 거리에서 최소가 최대보다 비싸지 않다
        for (int m = 500; m <= 20_000; m += 500) {
            assertThat(TransitFare.fare(kyoto.minBands(), m)).isLessThanOrEqualTo(TransitFare.fare(kyoto.maxBands(), m));
        }
    }

    @Test
    void 하코네는_1일권이_없고_타는_날마다_프리패스로_계산한다() throws Exception {
        FareProperties.CityFare hakone = load().forCity(City.HAKONE).orElseThrow();
        assertThat(hakone.dayYenIfRide()).isEqualTo(6000);

        // 이틀 다 타면 2일권 6,000 (1일권은 없음)
        TransitFare.PassPlan plan = TransitFare.bestPasses(List.of(6000, 6000), hakone);
        assertThat(plan.passes()).containsExactly(new TransitFare.Pass(1, 2, 48, 6000, "하코네 프리패스 (오다와라발)"));
    }

    @Test
    void 패스_값이_0이면_그_패스는_쓰지_않는다() {
        FareProperties.CityFare noPass = new FareProperties.CityFare(
                List.of(new FareProperties.Band(99, 240)), List.of(new FareProperties.Band(99, 240)), 0, 0, 0, null, 0);

        assertThat(TransitFare.bestPasses(List.of(2400, 2400), noPass).passes()).isEmpty();
    }
}
