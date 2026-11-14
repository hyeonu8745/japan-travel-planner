package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.trip.entity.Airport;
import com.example.japantravelplanner.trip.entity.Trip;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 공항 이동 (10-15 사용자 결정, 숙소 귀가 이동은 넣지 않고 공항만).
 *
 * 첫날: 비행기 도착 → 입국·짐 찾기 → 공항에서 숙소로 → 숙소에 짐 맡기기(체크인 전) → 관광 시작
 * 마지막 날: 아침 체크아웃·짐 맡기기 → (숙소 근처 쇼핑) → 숙소에서 짐 찾기 → 공항으로 → 비행기 출발
 *
 * 시각은 비행기 시각에서 앞뒤로 계산한다. 공항 ↔ 숙소 시간은 공항 + 도시별 대표값({@link Airport#transferMinutes}).
 * 일정 항목(stop)으로 저장하지 않고 응답에서 계산해 보여준다 (StopType을 늘리면 DB CHECK 제약 SQL이 필요하고,
 * 수정 서비스들이 이 항목을 옮기거나 지우지 않게 막아야 해서).
 * 분 단위 계산은 자정을 넘을 수 있으므로 시각으로 바꿀 때만 하루 안으로 접는다.
 */
public final class AirportPlan {

    static final int ARRIVAL_PROCESS = 60;     // 입국 심사·짐 찾기
    static final int HOTEL_DROP = 20;          // 숙소에 짐 맡기기·체크인
    static final int CHECK_IN_HOUR = 15;       // 일본 호텔 체크인 보통 15시
    static final int LUGGAGE_PICKUP = 15;      // 숙소에서 짐 찾기
    static final int LAST_STOP_TO_HOTEL = 30;  // 마지막 장소 → 숙소 (마지막 날은 숙소 근처라 짧게 잡는다)
    static final int CHECKOUT = 15;            // 아침 체크아웃·짐 맡기기

    private AirportPlan() {
    }

    /** @param position BEFORE = 그날 첫 장소 앞, AFTER = 마지막 장소 뒤 */
    public record Step(LocalTime time, String kind, String label, String position) {
    }

    /** 첫날 관광을 시작할 수 있는 시각(분). 비행기 도착 정보가 없으면 null */
    public static Integer firstDayStart(Trip trip) {
        return trip.arrivalAirportInfo()
                .map(a -> minutes(trip.getArrivalFlightTime()) + ARRIVAL_PROCESS + a.transferMinutes(trip.getBaseCity()) + HOTEL_DROP)
                .orElse(null);
    }

    /** 마지막 날 관광을 끝내야 하는 시각(분, 마지막 장소에서 떠나는 시각). 비행기 출발 정보가 없으면 null */
    public static Integer lastDayEnd(Trip trip) {
        return trip.departureAirportInfo()
                .map(a -> leaveHotel(trip, a) - LUGGAGE_PICKUP - LAST_STOP_TO_HOTEL)
                .orElse(null);
    }

    private static int leaveHotel(Trip trip, Airport a) {
        return minutes(trip.getDepartureFlightTime()) - a.beforeDepartureMinutes() - a.transferMinutes(trip.getBaseCity());
    }

    /** 첫날 일정표 앞에 붙는 공항 → 숙소 단계 */
    public static List<Step> arrivalSteps(Trip trip) {
        List<Step> steps = new ArrayList<>();
        trip.arrivalAirportInfo().ifPresent(a -> {
            int landed = minutes(trip.getArrivalFlightTime());
            int leave = landed + ARRIVAL_PROCESS;
            int atHotel = leave + a.transferMinutes(trip.getBaseCity());
            steps.add(new Step(time(landed), "FLIGHT_ARRIVAL", a.label() + " 도착", "BEFORE"));
            steps.add(new Step(time(leave), "AIRPORT_TO_HOTEL",
                    a.label() + " → 숙소 (입국·짐 찾기 후, 대중교통 약 " + a.transferMinutes(trip.getBaseCity()) + "분)", "BEFORE"));
            steps.add(new Step(time(atHotel), "HOTEL_DROP",
                    atHotel < CHECK_IN_HOUR * 60 ? "숙소에 짐 맡기기 (체크인 전)" : "숙소 체크인", "BEFORE"));
        });
        return steps;
    }

    /**
     * 마지막 날 앞뒤 단계: 아침 체크아웃(관광 시작 15분 전) / 숙소에서 짐 찾기 → 공항 → 출발
     * @param windowStart 그날 관광 시작 시각 (일정을 넣을 수 없는 날이면 null)
     */
    public static List<Step> departureSteps(Trip trip, LocalTime windowStart) {
        List<Step> steps = new ArrayList<>();
        trip.departureAirportInfo().ifPresent(a -> {
            int leave = leaveHotel(trip, a);
            int pickup = leave - LUGGAGE_PICKUP;
            int checkout = windowStart == null ? pickup - CHECKOUT : Math.min(minutes(windowStart) - CHECKOUT, pickup - CHECKOUT);
            steps.add(new Step(time(checkout), "CHECKOUT", "숙소 체크아웃 · 짐 맡기기", "BEFORE"));
            steps.add(new Step(time(pickup), "HOTEL_PICKUP", "숙소에서 짐 찾기", "AFTER"));
            steps.add(new Step(time(leave), "HOTEL_TO_AIRPORT",
                    "숙소 → " + a.label() + " (대중교통 약 " + a.transferMinutes(trip.getBaseCity()) + "분)", "AFTER"));
            steps.add(new Step(trip.getDepartureFlightTime(), "FLIGHT_DEPARTURE",
                    "비행기 출발 (공항에는 " + hours(a.beforeDepartureMinutes()) + " 전 도착, 면세품 환급 세관 확인 시간 포함)", "AFTER"));
        });
        return steps;
    }

    /** 180 → "3시간", 150 → "2시간 30분" */
    static String hours(int minutes) {
        return minutes % 60 == 0 ? minutes / 60 + "시간" : minutes / 60 + "시간 " + minutes % 60 + "분";
    }

    static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    /** 자정을 넘거나 전날로 넘어간 분도 하루 안의 시각으로 */
    static LocalTime time(int minutes) {
        int m = Math.floorMod(minutes, 24 * 60);
        return LocalTime.of(m / 60, m % 60);
    }
}
