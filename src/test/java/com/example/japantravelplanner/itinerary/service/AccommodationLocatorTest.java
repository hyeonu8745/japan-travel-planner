package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.global.exception.BadRequestException;
import com.example.japantravelplanner.global.google.GoogleGeocodingClient;
import com.example.japantravelplanner.global.google.GoogleGeocodingClient.GeocodeResult;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.Region;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** 숙소 위치 → 여행의 기준 도시·권역 (11-18 확장) */
class AccommodationLocatorTest {

    private final GoogleGeocodingClient geocoding = mock(GoogleGeocodingClient.class);
    private final AccommodationLocator locator = new AccommodationLocator(geocoding);

    private static Trip trip(String title) {
        return Trip.builder().title(title).accommodationName("호텔").accommodationAddress("주소").build();
    }

    @Test
    void 오사카_숙소면_기준_도시가_오사카_권역이_간사이가_되고_기본_제목도_바뀐다() {
        when(geocoding.geocode(anyString())).thenReturn(Optional.of(new GeocodeResult(34.6687, 135.5013, "難波")));
        Trip trip = trip("도쿄 3박 4일");

        locator.locate(trip);

        assertThat(trip.getBaseCity()).isEqualTo(City.OSAKA);
        assertThat(trip.getRegion()).isEqualTo(Region.KANSAI);
        assertThat(trip.getTitle()).isEqualTo("오사카 3박 4일");
        assertThat(trip.getAccommodationLocation().getY()).isEqualTo(34.6687);
    }

    @Test
    void 사용자가_바꾼_제목은_그대로_둔다() {
        when(geocoding.geocode(anyString())).thenReturn(Optional.of(new GeocodeResult(35.0116, 135.7681, "京都")));
        Trip trip = trip("우리 가족 여행");

        locator.locate(trip);

        assertThat(trip.getBaseCity()).isEqualTo(City.KYOTO);
        assertThat(trip.getTitle()).isEqualTo("우리 가족 여행");
    }

    @Test
    void 서비스_도시_밖이면_거절한다() {
        when(geocoding.geocode("센다이")).thenReturn(Optional.of(new GeocodeResult(38.2682, 140.8694, "仙台")));
        Trip sendai = Trip.builder().title("도쿄 여행").accommodationName("호텔").accommodationAddress("센다이").build();

        assertThatThrownBy(() -> locator.locate(sendai)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("서비스하는 도시 범위 밖");
    }

    @Test
    void 좌표가_이미_있으면_Google을_부르지_않고_도시만_맞춘다() {
        Trip trip = trip("도쿄 당일치기");
        trip.setAccommodationLocation(new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(130.4207, 33.5897)));   // 博多駅

        locator.locate(trip);

        verify(geocoding, never()).geocode(anyString());
        assertThat(trip.getBaseCity()).isEqualTo(City.FUKUOKA);
        assertThat(trip.getTitle()).isEqualTo("후쿠오카 당일치기");
    }
}
