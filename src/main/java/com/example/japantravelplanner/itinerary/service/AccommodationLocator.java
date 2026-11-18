package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.global.exception.BadRequestException;
import com.example.japantravelplanner.global.google.GoogleGeocodingClient;
import com.example.japantravelplanner.global.google.GoogleGeocodingClient.GeocodeResult;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

/**
 * 일정 생성 ① 숙소 위치 확인
 * - 이미 좌표가 있으면 Google을 다시 부르지 않는다
 * - 주소로 못 찾으면 숙소 이름으로 한 번 더
 * - 서비스하는 19개 도시 범위 밖이면 거절 (오타로 엉뚱한 지역이 잡히는 경우)
 * - 숙소가 있는 도시를 여행의 기준 도시·권역으로 정한다 (11-18: 일정·공항·교통비가 이 도시 값을 쓴다)
 */
@Component
@RequiredArgsConstructor
public class AccommodationLocator {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final GoogleGeocodingClient geocodingClient;

    /** 숙소 좌표를 확인해서 Trip에 채우고, 숙소가 있는 도시를 기준 도시로 정한다. 호출하는 쪽의 트랜잭션 안에서 저장됨 */
    public Point locate(Trip trip) {
        Point saved = trip.getAccommodationLocation();
        if (saved != null) {
            City.containing(saved.getY(), saved.getX()).ifPresent(city -> applyCity(trip, city));
            return saved;
        }

        GeocodeResult result = geocodingClient.geocode(trip.getAccommodationAddress())
                .or(() -> geocodingClient.geocode(trip.getAccommodationName() + " 日本"))
                .orElseThrow(() -> new BadRequestException(
                        "숙소 주소를 찾지 못했어요. 여행 조건에서 숙소 이름이나 주소를 확인해주세요."));

        City city = City.containing(result.lat(), result.lng())
                .orElseThrow(() -> new BadRequestException(
                        "숙소가 서비스하는 도시 범위 밖이에요. 주소를 확인해주세요. (찾은 주소: " + result.formattedAddress() + ")"));

        // JTS 좌표는 (경도, 위도) 순서
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(result.lng(), result.lat()));
        trip.setAccommodationLocation(point);
        applyCity(trip, city);
        return point;
    }

    /** 숙소 도시를 여행의 기준 도시·권역으로 */
    private static void applyCity(Trip trip, City city) {
        trip.setBaseCity(city);
        trip.setRegion(city.region());
        trip.renameDefaultTitle(city);
    }
}