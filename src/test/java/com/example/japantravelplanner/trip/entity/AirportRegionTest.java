package com.example.japantravelplanner.trip.entity;

import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-08 리팩터링: 공항에 권역을 붙였다. 도쿄 여행은 예전과 똑같이 나리타·하네다만 받는다 */
class AirportRegionTest {

    @Test
    void 공항은_권역별로_있고_도쿄_여행은_나리타_하네다만_받는다() {
        assertThat(Airport.isKnownOrBlank("nrt", Region.KANTO)).isTrue();
        assertThat(Airport.isKnownOrBlank("HND", Region.KANTO)).isTrue();
        assertThat(Airport.isKnownOrBlank("KIX", Region.KANTO)).isFalse();
        assertThat(Airport.isKnownOrBlank("KIX", Region.KANSAI)).isTrue();
        assertThat(Airport.isKnownOrBlank("ICN", Region.KANTO)).isFalse();
        assertThat(Airport.isKnownOrBlank(" ", Region.KANTO)).isTrue();
        assertThat(Airport.isKnownOrBlank(null, Region.OKINAWA)).isTrue();
        for (Region r : Region.values()) {
            assertThat(java.util.Arrays.stream(Airport.values()).anyMatch(a -> a.region() == r)).as(r.name()).isTrue();
        }
    }

    @Test
    void 도쿄_공항_이동_시간은_예전_값_그대로이고_값이_없는_도시는_60분() {
        assertThat(Airport.NRT.transferMinutes(City.TOKYO)).isEqualTo(AirportAccess.of(Airport.NRT, City.TOKYO).orElseThrow().minutes());
        assertThat(Airport.HND.transferMinutes(City.TOKYO)).isEqualTo(AirportAccess.of(Airport.HND, City.TOKYO).orElseThrow().minutes());
        // 값이 없는 짝은 60분 (11-22 하네다 → 가마쿠라·나리타 → 하코네는 확인해서 넣었다)
        assertThat(AirportAccess.of(Airport.HND, City.NARA)).isEmpty();
        assertThat(Airport.HND.transferMinutes(City.NARA)).isEqualTo(60);
        assertThat(AirportAccess.of(Airport.HND, City.KAMAKURA).orElseThrow().cheapYen()).isEqualTo(837);
        assertThat(AirportAccess.of(Airport.NRT, City.HAKONE).orElseThrow().fastYen()).isEqualTo(9547);
        assertThat(AirportAccess.of(Airport.HND, City.YOKOHAMA).orElseThrow().cheapYen()).isEqualTo(400);
    }

    @Test
    void 확장_도시는_권역_공항에서_가는_값이_있다() {
        // 11-18: 교통비 조사 문서(공식 확인) 값
        assertThat(AirportAccess.of(Airport.KIX, City.OSAKA).orElseThrow().cheapYen()).isEqualTo(970);
        assertThat(AirportAccess.of(Airport.KIX, City.OSAKA).orElseThrow().fastYen()).isEqualTo(1670);
        assertThat(AirportAccess.of(Airport.KIX, City.KYOTO).orElseThrow().cheapYen()).isEqualTo(2200);
        assertThat(AirportAccess.of(Airport.CTS, City.SAPPORO).orElseThrow().cheapYen()).isEqualTo(1230);
        assertThat(AirportAccess.of(Airport.KMQ, City.KANAZAWA).orElseThrow().cheapYen()).isEqualTo(1400);
        for (City city : List.of(City.OSAKA, City.KYOTO, City.KOBE, City.NARA, City.NAGOYA, City.KANAZAWA, City.TAKAYAMA,
                City.HIROSHIMA, City.FUKUOKA, City.BEPPU, City.YUFUIN, City.SAPPORO, City.OTARU, City.NAHA)) {
            boolean any = java.util.Arrays.stream(Airport.values())
                    .filter(a -> a.region() == city.region())
                    .anyMatch(a -> AirportAccess.of(a, city).isPresent());
            assertThat(any).as(city.name()).isTrue();
        }
    }

    @Test
    void 공항_이동_값은_권역이_맞는_공항만_있다() {
        for (Airport a : Airport.values()) {
            for (City c : City.values()) {
                AirportAccess.of(a, c).ifPresent(x -> assertThat(a.region()).as(a + "→" + c).isEqualTo(c.region()));
            }
        }
    }

    @Test
    void 여행은_권역과_기준_도시가_없으면_수도권_도쿄() {
        Trip trip = Trip.builder().build();
        assertThat(trip.getRegion()).isEqualTo(Region.KANTO);
        assertThat(trip.getBaseCity()).isEqualTo(City.TOKYO);
    }

    @Test
    void 여행_권역의_공항이_아니면_비행기_정보로_쓰지_않는다() {
        Trip tokyo = Trip.builder().arrivalAirport("KIX").arrivalFlightTime(LocalTime.of(10, 0))
                .departureAirport("HND").departureFlightTime(LocalTime.of(18, 0)).build();
        assertThat(tokyo.arrivalAirportInfo()).isEmpty();
        assertThat(tokyo.departureAirportInfo()).contains(Airport.HND);

        Trip osaka = Trip.builder().region(Region.KANSAI).baseCity(City.OSAKA)
                .arrivalAirport("KIX").arrivalFlightTime(LocalTime.of(10, 0)).build();
        assertThat(osaka.arrivalAirportInfo()).contains(Airport.KIX);
    }

    @Test
    void 기본_제목만_숙소_도시_이름으로_바뀐다() {
        Trip a = Trip.builder().title("도쿄 3박 4일").build();
        Trip b = Trip.builder().title("도쿄 당일치기").build();
        Trip c = Trip.builder().title("도쿄 여행").build();
        Trip mine = Trip.builder().title("도쿄 3박 4일 엄마랑").build();

        for (Trip t : List.of(a, b, c, mine)) t.renameDefaultTitle(City.NAHA);

        assertThat(a.getTitle()).isEqualTo("나하 3박 4일");
        assertThat(b.getTitle()).isEqualTo("나하 당일치기");
        assertThat(c.getTitle()).isEqualTo("나하 여행");
        assertThat(mine.getTitle()).isEqualTo("도쿄 3박 4일 엄마랑");
    }
}
