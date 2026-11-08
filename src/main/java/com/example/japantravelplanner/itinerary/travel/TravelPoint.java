package com.example.japantravelplanner.itinerary.travel;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.locationtech.jts.geom.Point;

/**
 * 이동 시간을 계산할 장소 하나.
 * 캐시 키는 좌표를 소수점 4자리(약 11m)로 반올림해서, 같은 장소면 같은 키가 되게 한다.
 * 11-08 리팩터링: 수정 서비스 3곳에 복사돼 있던 pointOf 를 아래 정적 메서드로 모았다.
 */
public record TravelPoint(String name, double lat, double lng) {

    /** JTS 좌표는 (경도 x, 위도 y) 순서 */
    public static TravelPoint of(String name, Point point) {
        return new TravelPoint(name, point.getY(), point.getX());
    }

    public static TravelPoint of(Attraction attraction) {
        return of(attraction.getName(), attraction.getLocation());
    }

    public static TravelPoint of(Restaurant restaurant) {
        return of(restaurant.getName(), restaurant.getLocation());
    }

    /** 일정 항목: 관광지면 관광지, 나머지(점심·저녁·카페)는 식당 위치 */
    public static TravelPoint of(ItineraryStop stop) {
        return stop.getType() == StopType.ATTRACTION ? of(stop.getAttraction()) : of(stop.getRestaurant());
    }

    /** 두 장소의 가운데 (끼니·카페를 앞뒤 장소 사이에서 찾을 때) */
    public static TravelPoint midpoint(String name, TravelPoint a, TravelPoint b) {
        return new TravelPoint(name, (a.lat() + b.lat()) / 2, (a.lng() + b.lng()) / 2);
    }
}
