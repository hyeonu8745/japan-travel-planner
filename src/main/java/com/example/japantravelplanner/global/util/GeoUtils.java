package com.example.japantravelplanner.global.util;

import org.locationtech.jts.geom.Point;

/** 위경도 유틸: 거리 계산(Haversine 공식), JTS 점에서 위도·경도 꺼내기. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoUtils() {
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }

    /** JTS 좌표는 (경도 x, 위도 y) 순서. 점이 없으면 null (응답 DTO용) */
    public static Double lat(Point point) {
        return point == null ? null : point.getY();
    }

    public static Double lng(Point point) {
        return point == null ? null : point.getX();
    }
}
