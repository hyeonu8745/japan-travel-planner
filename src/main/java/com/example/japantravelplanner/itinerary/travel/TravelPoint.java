package com.example.japantravelplanner.itinerary.travel;

/**
 * 이동 시간을 계산할 장소 하나.
 * 캐시 키는 좌표를 소수점 4자리(약 11m)로 반올림해서, 같은 장소면 같은 키가 되게 한다.
 */
public record TravelPoint(String name, double lat, double lng) {

}