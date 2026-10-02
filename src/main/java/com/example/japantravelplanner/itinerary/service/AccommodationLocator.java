package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.global.google.GoogleGeocodingClient;
import com.example.japantravelplanner.global.google.GoogleGeocodingClient.GeocodeResult;
import com.example.japantravelplanner.trip.entity.Trip;
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
 * - 도쿄 중심에서 너무 먼 결과는 거절 (오타로 엉뚱한 지역이 잡히는 경우)
 */
@Component
@RequiredArgsConstructor
public class AccommodationLocator {

    // 도쿄역 기준 반경. 이케부쿠로·시나가와 숙소까지는 허용
    private static final double TOKYO_STATION_LAT = 35.6812;
    private static final double TOKYO_STATION_LNG = 139.7671;
    private static final double MAX_DISTANCE_FROM_TOKYO_M = 15_000;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final GoogleGeocodingClient geocodingClient;

    /** 숙소 좌표를 확인해서 Trip에 채운다. 호출하는 쪽의 트랜잭션 안에서 저장됨 */
    public Point locate(Trip trip) {
        if (trip.getAccommodationLocation() != null) {
            return trip.getAccommodationLocation();
        }

        GeocodeResult result = geocodingClient.geocode(trip.getAccommodationAddress())
                .or(() -> geocodingClient.geocode(trip.getAccommodationName() + " 東京"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "숙소 주소를 찾지 못했어요. 여행 조건에서 숙소 이름이나 주소를 확인해주세요."));

        if (distance(result.lat(), result.lng()) > MAX_DISTANCE_FROM_TOKYO_M) {
            throw new IllegalArgumentException(
                    "숙소가 도쿄 서비스 범위에서 너무 멀어요. 주소를 확인해주세요. (찾은 주소: " + result.formattedAddress() + ")");
        }

        // JTS 좌표는 (경도, 위도) 순서
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(result.lng(), result.lat()));
        trip.setAccommodationLocation(point);
        return point;
    }

    private double distance(double lat, double lng) {
        double dLat = Math.toRadians(lat - TOKYO_STATION_LAT);
        double dLng = Math.toRadians(lng - TOKYO_STATION_LNG);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(TOKYO_STATION_LAT)) * Math.cos(Math.toRadians(lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
    }
}