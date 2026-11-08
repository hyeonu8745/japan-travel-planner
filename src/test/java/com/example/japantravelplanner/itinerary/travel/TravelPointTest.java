package com.example.japantravelplanner.itinerary.travel;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-08 리팩터링: 수정 서비스 3곳의 pointOf·lat·lng 를 모은 TravelPoint.of / GeoUtils.lat·lng */
class TravelPointTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final Attraction tower = Attraction.builder().id(1L).name("東京タワー")
            .location(GF.createPoint(new Coordinate(139.7454, 35.6586))).build();
    private final Restaurant ramen = Restaurant.builder().id(2L).name("ラーメン屋")
            .location(GF.createPoint(new Coordinate(139.7000, 35.6900))).build();

    @Test
    void JTS_점은_경도_x_위도_y_순서로_읽는다() {
        TravelPoint p = TravelPoint.of(tower);
        assertThat(p.name()).isEqualTo("東京タワー");
        assertThat(p.lat()).isEqualTo(35.6586);
        assertThat(p.lng()).isEqualTo(139.7454);
    }

    @Test
    void 일정_항목은_관광지면_관광지_끼니면_식당_위치() {
        ItineraryStop sight = ItineraryStop.builder().type(StopType.ATTRACTION).attraction(tower).build();
        ItineraryStop lunch = ItineraryStop.builder().type(StopType.LUNCH).restaurant(ramen).build();
        ItineraryStop cafe = ItineraryStop.builder().type(StopType.CAFE).restaurant(ramen).build();

        assertThat(TravelPoint.of(sight)).isEqualTo(TravelPoint.of(tower));
        assertThat(TravelPoint.of(lunch)).isEqualTo(TravelPoint.of(ramen));
        assertThat(TravelPoint.of(cafe)).isEqualTo(TravelPoint.of(ramen));
    }

    @Test
    void 가운데_지점() {
        TravelPoint mid = TravelPoint.midpoint("중간", new TravelPoint("a", 35.0, 139.0), new TravelPoint("b", 36.0, 140.0));
        assertThat(mid).isEqualTo(new TravelPoint("중간", 35.5, 139.5));
    }

    @Test
    void 응답용_위도_경도는_점이_없으면_null() {
        assertThat(GeoUtils.lat(null)).isNull();
        assertThat(GeoUtils.lng(null)).isNull();
        assertThat(GeoUtils.lat(tower.getLocation())).isEqualTo(35.6586);
        assertThat(GeoUtils.lng(tower.getLocation())).isEqualTo(139.7454);
    }
}
