package com.example.japantravelplanner.itinerary.fare;

import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.trip.entity.AirportAccess;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 일정 → 교통비 추정 (10-16). 저장하지 않고 식비 요약을 볼 때마다 계산한다.
 *
 * - 지하철: 일정의 대중교통(TRANSIT) 구간마다 거리별 운임. 하루 끝 마지막 장소 → 숙소도 걷기엔 멀면 한 구간 더한다
 *   (일정에는 숙소로 돌아가는 구간이 없다). 메트로 운임을 최소, 도에이 운임을 최대로 본다.
 * - 1일권: 날짜별 최대 요금으로 24·48·72시간권 조합을 찾고, 그냥 타는 것보다 싸면 안내한다.
 * - 공항: 여행 조건에 비행기 시각이 있으면 도착·출발 각각 저렴한 방법 ~ 빠른 방법 요금.
 * - 렌터카 여행은 지하철 요금을 넣지 않는다 (렌터카 비용은 모름).
 */
public final class TransportEstimator {

    private TransportEstimator() {
    }

    /**
     * @param walkLimitMeters 숙소로 돌아갈 때 이 직선거리 안이면 걷는다고 본다
     * @param transitDetour   직선거리 → 대중교통 거리 (TravelProperties.transitDetour)
     */
    public static TransportEstimate estimate(Trip trip, Itinerary itinerary, FareProperties props, double krwPerYen,
                                             int travelers, double walkLimitMeters, double transitDetour) {
        boolean rentalCar = trip.getTransportMode() == TransportMode.RENTAL_CAR;
        Point hotel = trip.getAccommodationLocation();
        // 숙소 도시의 운임 (11-08). 확장 전에는 도쿄 값만 있어서, 값이 없는 도시도 도쿄 값으로 계산한다
        FareProperties.CityFare fare = props.forCity(trip.getBaseCity())
                .orElseGet(() -> props.forCity(City.TOKYO).orElseThrow());

        List<TransportEstimate.Day> days = new ArrayList<>();
        List<Integer> dayMaxFares = new ArrayList<>();
        int legs = 0, min = 0, max = 0;
        List<ItineraryDay> ordered = itinerary.getDays().stream()
                .sorted(Comparator.comparingInt(ItineraryDay::getDayNumber)).toList();
        for (ItineraryDay day : ordered) {
            int dayLegs = 0, dayMin = 0, dayMax = 0;
            if (!rentalCar) {
                List<Integer> distances = new ArrayList<>();
                for (ItineraryStop stop : day.getStops()) {
                    if (stop.getTravelMode() == LegMode.TRANSIT) distances.add(stop.getTravelDistanceMeters());
                }
                Integer back = returnDistance(day, hotel, walkLimitMeters, transitDetour);
                if (back != null) distances.add(back);
                for (int d : distances) {
                    dayLegs++;
                    dayMin += TransitFare.fare(fare.minBands(), d);
                    dayMax += TransitFare.fare(fare.maxBands(), d);
                }
            }
            legs += dayLegs;
            min += dayMin;
            max += dayMax;
            dayMaxFares.add(dayMax);
            days.add(new TransportEstimate.Day(day.getDayNumber(), day.getDate(), dayLegs, dayMin, dayMax));
        }

        TransitFare.PassPlan plan = TransitFare.bestPasses(dayMaxFares, fare);
        boolean usePass = !plan.passes().isEmpty() && plan.savings() > 0;
        int passCost = usePass ? plan.cost() : max;
        TransportEstimate.Subway subway = new TransportEstimate.Subway(legs, min, max,
                usePass ? plan.passes() : List.of(), passCost, usePass ? plan.savings() : 0,
                Math.min(min, passCost), Math.min(max, passCost));

        TransportEstimate.Airport airport = airport(trip);
        int personMin = subway.finalMinYen() + (airport == null ? 0 : airport.minYen());
        int personMax = subway.finalMaxYen() + (airport == null ? 0 : airport.maxYen());
        return new TransportEstimate(range(personMin, personMax, krwPerYen),
                range(personMin * travelers, personMax * travelers, krwPerYen),
                subway, airport, rentalCar, List.copyOf(days), TransportEstimate.NOTE);
    }

    /** 하루 마지막 장소 → 숙소가 걷기엔 멀면 그 대중교통 거리, 아니면 null */
    static Integer returnDistance(ItineraryDay day, Point hotel, double walkLimitMeters, double transitDetour) {
        if (hotel == null || day.getStops().isEmpty()) return null;
        ItineraryStop last = day.getStops().stream().max(Comparator.comparingInt(ItineraryStop::getStopOrder)).orElseThrow();
        Point at = last.getAttraction() != null ? last.getAttraction().getLocation()
                : last.getRestaurant() != null ? last.getRestaurant().getLocation() : null;
        if (at == null) return null;
        double straight = GeoUtils.distanceMeters(at.getY(), at.getX(), hotel.getY(), hotel.getX());
        return straight <= walkLimitMeters ? null : (int) Math.round(straight * transitDetour);
    }

    static TransportEstimate.Airport airport(Trip trip) {
        TransportEstimate.Leg arrival = trip.arrivalAirportInfo().flatMap(a -> AirportAccess.of(a, trip.getBaseCity()))
                .map(TransportEstimator::leg).orElse(null);
        TransportEstimate.Leg departure = trip.departureAirportInfo().flatMap(a -> AirportAccess.of(a, trip.getBaseCity()))
                .map(TransportEstimator::leg).orElse(null);
        if (arrival == null && departure == null) return null;
        int min = (arrival == null ? 0 : arrival.cheapYen()) + (departure == null ? 0 : departure.cheapYen());
        int max = (arrival == null ? 0 : arrival.fastYen()) + (departure == null ? 0 : departure.fastYen());
        return new TransportEstimate.Airport(arrival, departure, min, max);
    }

    private static TransportEstimate.Leg leg(AirportAccess a) {
        return new TransportEstimate.Leg(a.airport().label(), a.cheapYen(), a.cheapLabel(), a.fastYen(), a.fastLabel());
    }

    private static TransportEstimate.Range range(int minYen, int maxYen, double krwPerYen) {
        return new TransportEstimate.Range(minYen, maxYen,
                (int) Math.round(minYen * krwPerYen), (int) Math.round(maxYen * krwPerYen));
    }
}
