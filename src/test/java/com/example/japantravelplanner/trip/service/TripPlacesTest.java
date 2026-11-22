package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.BadRequestException;
import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 여행 조건의 기준 도시·권역·당일치기 도시·공항 권역 (11-22 확장 5단계) */
class TripPlacesTest {

    private static TripRequestDto request(String name, String address) {
        TripRequestDto r = new TripRequestDto();
        r.setAccommodationName(name);
        r.setAccommodationAddress(address);
        return r;
    }

    @Test
    void 숙소_주소에_도시_이름이_있으면_그_도시와_권역() {
        TripPlaces places = TripPlaces.resolve(request("호텔", "大阪府大阪市中央区難波5-1-60"), null);

        assertThat(places.city()).isEqualTo(City.OSAKA);
        assertThat(places.region()).isEqualTo(Region.KANSAI);
        assertThat(places.dayTrips()).isEmpty();
    }

    @Test
    void 도시를_모르면_고른_권역의_대표_도시_권역도_없으면_지금_도시나_도쿄() {
        TripRequestDto kansai = request("Hotel ABC", "1-2-3 Chuo");
        kansai.setRegion("kansai");
        assertThat(TripPlaces.resolve(kansai, null).city()).isEqualTo(City.OSAKA);
        assertThat(TripPlaces.resolve(kansai, City.KYOTO).city()).isEqualTo(City.KYOTO);   // 같은 권역이면 지금 도시
        assertThat(TripPlaces.resolve(kansai, City.TOKYO).city()).isEqualTo(City.OSAKA);

        TripRequestDto none = request("Hotel ABC", "1-2-3 Chuo");
        assertThat(TripPlaces.resolve(none, null).city()).isEqualTo(City.TOKYO);
        assertThat(TripPlaces.resolve(none, City.FUKUOKA).city()).isEqualTo(City.FUKUOKA);
    }

    @Test
    void 고른_권역과_숙소_도시가_다르면_400() {
        TripRequestDto r = request("호텔", "京都府京都市下京区");
        r.setRegion("KANTO");

        assertThatThrownBy(() -> TripPlaces.resolve(r, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("숙소 주소의 도시(교토)가 고른 권역(수도권)에 없어요. 권역이나 숙소를 확인해주세요.");
    }

    @Test
    void 공항은_숙소_권역의_공항만() {
        TripRequestDto r = request("호텔", "大阪府大阪市北区");
        r.setArrivalAirport("NRT");

        assertThatThrownBy(() -> TripPlaces.resolve(r, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("간사이 여행은 간사이 국제공항(KIX), 고베 공항(UKB) 중에서 공항을 골라주세요.");

        r.setArrivalAirport("kix");
        r.setDepartureAirport("UKB");
        assertThat(TripPlaces.resolve(r, null).city()).isEqualTo(City.OSAKA);
    }

    @Test
    void 당일치기_도시는_갈_수_있는_곳만_같은_도시와_숙소_도시는_한_번만_뺀다() {
        TripRequestDto r = request("호텔", "大阪府大阪市北区");
        r.setDayTripCities(List.of("KYOTO", "OSAKA", "kyoto", "NARA"));
        assertThat(TripPlaces.resolve(r, null).dayTrips()).containsExactly(City.KYOTO, City.NARA);

        r.setDayTripCities(List.of("SAPPORO"));
        assertThatThrownBy(() -> TripPlaces.resolve(r, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("오사카에서 당일치기로 갈 수 있는 도시는 고베, 교토, 나라예요.");

        TripRequestDto naha = request("호텔", "沖縄県那覇市");
        naha.setDayTripCities(List.of("FUKUOKA"));
        assertThatThrownBy(() -> TripPlaces.resolve(naha, null))
                .hasMessage("나하에서는 당일치기로 다녀올 도시가 없어요.");
    }

    @Test
    void 요청_검증_권역_이름과_당일치기_도시_이름() {
        TripRequestDto r = request("호텔", "주소");
        r.setRegion("KANSAI");
        r.setDayTripCities(List.of("KYOTO"));
        assertThat(r.isRegionKnown()).isTrue();
        assertThat(r.isDayTripCitiesKnown()).isTrue();

        r.setRegion("TOHOKU");
        r.setDayTripCities(List.of("SENDAI"));
        assertThat(r.isRegionKnown()).isFalse();
        assertThat(r.isDayTripCitiesKnown()).isFalse();
    }

    @Test
    void 고칠_때_당일치기_도시를_보내지_않으면_지금_도시를_두고_갈_수_없게_된_도시만_뺀다() {
        TripRequestDto r = request("호텔", "大阪府大阪市北区");   // dayTripCities 없음
        assertThat(TripPlaces.resolve(r, City.OSAKA, List.of(City.KYOTO, City.NARA)).dayTrips())
                .containsExactly(City.KYOTO, City.NARA);

        TripRequestDto kyoto = request("호텔", "京都府京都市下京区");   // 숙소를 교토로 옮김 → 교토 자신은 빠진다
        assertThat(TripPlaces.resolve(kyoto, City.OSAKA, List.of(City.KYOTO, City.NARA)).dayTrips())
                .containsExactly(City.NARA);

        TripRequestDto cleared = request("호텔", "大阪府大阪市北区");
        cleared.setDayTripCities(List.of());
        assertThat(TripPlaces.resolve(cleared, City.OSAKA, List.of(City.KYOTO)).dayTrips()).isEmpty();
    }

    @Test
    void 고른_숙소_도시가_있으면_그_도시이고_주소나_권역과_다르면_400() {
        TripRequestDto r = request("Hotel ABC", "1-2-3 Chuo");   // 주소로는 도시를 알 수 없음
        r.setRegion("KANSAI");
        r.setBaseCity("kyoto");
        r.setDayTripCities(List.of("NARA"));
        TripPlaces places = TripPlaces.resolve(r, null);
        assertThat(places.city()).isEqualTo(City.KYOTO);
        assertThat(places.dayTrips()).containsExactly(City.NARA);

        r.setRegion("KANTO");
        assertThatThrownBy(() -> TripPlaces.resolve(r, null))
                .hasMessage("숙소 도시(교토)가 고른 권역(수도권)에 없어요. 권역이나 숙소 도시를 확인해주세요.");

        TripRequestDto osakaAddress = request("호텔", "大阪府大阪市北区");
        osakaAddress.setBaseCity("KYOTO");
        assertThatThrownBy(() -> TripPlaces.resolve(osakaAddress, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("숙소 주소는 오사카인데 숙소 도시를 교토(으)로 골랐어요. 숙소 도시나 주소를 확인해주세요.");

        osakaAddress.setBaseCity("SENDAI");
        assertThat(osakaAddress.isBaseCityKnown()).isFalse();
    }
}
