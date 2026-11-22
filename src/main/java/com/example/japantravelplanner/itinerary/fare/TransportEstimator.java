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
import com.example.japantravelplanner.trip.region.DayTripRoute;
import com.example.japantravelplanner.itinerary.planner.DayTrips;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 일정 → 교통비 추정. 저장하지 않고 식비 요약을 볼 때마다 계산한다.
 *
 * - 대중교통: 일정의 대중교통(TRANSIT) 구간마다 숙소 도시의 운임. 하루 끝 마지막 장소 → 숙소도 걷기엔 멀면 한 구간 더한다
 *   (일정에는 숙소로 돌아가는 구간이 없다). 도쿄는 메트로 운임을 최소, 도에이 운임을 최대로 본다 (도시별은 FareProperties).
 * - 1일권: 날짜별 최대 요금으로 그 도시의 24·48·72시간권(있는 것만) 조합을 찾고, 그냥 타는 것보다 싸면 안내한다.
 * - 공항: 여행 조건에 비행기 시각이 있으면 도착·출발 각각 저렴한 방법 ~ 빠른 방법 요금.
 * - 렌터카 여행은 지하철 요금을 넣지 않는다 (렌터카 비용은 모름).
 * - 당일치기 날(11-22): 숙소 도시 역 ↔ 그 도시 역 왕복 운임(DayTripRoute) + 그 도시 운임으로 그 도시 안 이동.
 *   하루 끝은 숙소가 아니라 그 도시 역으로 돌아온다고 본다. 숙소 도시 1일권 계산에는 넣지 않는다 (그날은 그 도시에 있다).
 */
public final class TransportEstimator {

    /** 정적 메서드만 쓴다 */
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
        // 숙소 도시의 운임 (값이 없는 도시는 도쿄 값으로 계산한다)
        FareProperties.CityFare fare = props.forCity(trip.getBaseCity())
                .orElseGet(() -> props.forCity(City.TOKYO).orElseThrow());

        List<TransportEstimate.Day> days = new ArrayList<>();
        List<TransportEstimate.DayTrip> dayTrips = new ArrayList<>();
        List<Integer> dayMaxFares = new ArrayList<>();
        int legs = 0, min = 0, max = 0, tripMin = 0, tripMax = 0;
        List<ItineraryDay> ordered = itinerary.getDays().stream()
                .sorted(Comparator.comparingInt(ItineraryDay::getDayNumber)).toList();
        for (ItineraryDay day : ordered) {
            Optional<DayTripRoute> dayTrip = DayTrips.routeOf(trip, day.getZone());
            City dayCity = day.getZone() == null ? trip.getBaseCity() : day.getZone().getCity();
            FareProperties.CityFare dayFare = dayTrip.isPresent() ? props.forCity(dayCity).orElse(fare) : fare;
            double[] end = dayTrip.map(r -> {
                DayTripRoute.Station s = r.stationOf(dayCity);
                return new double[]{s.lat(), s.lng()};
            }).orElse(hotel == null ? null : new double[]{hotel.getY(), hotel.getX()});

            int dayLegs = 0, dayMin = 0, dayMax = 0;
            if (!rentalCar) {
                List<Integer> distances = new ArrayList<>();
                for (ItineraryStop stop : day.getStops()) {
                    if (stop.getTravelMode() == LegMode.TRANSIT) distances.add(stop.getTravelDistanceMeters());
                }
                Integer back = end == null ? null : returnDistance(day, end[0], end[1], walkLimitMeters, transitDetour);
                if (back != null) distances.add(back);
                for (int d : distances) {
                    dayLegs++;
                    dayMin += TransitFare.fare(dayFare.minBands(), d);
                    dayMax += TransitFare.fare(dayFare.maxBands(), d);
                }
                if (dayFare.dayYenIfRide() > 0 && dayLegs > 0) {   // 패스로만 다니는 곳: 타는 날은 패스 값
                    dayMin = dayFare.dayYenIfRide();
                    dayMax = dayFare.dayYenIfRide();
                }
            }
            days.add(new TransportEstimate.Day(day.getDayNumber(), day.getDate(), dayLegs, dayMin, dayMax));
            if (dayTrip.isPresent()) {
                if (!rentalCar) {
                    int roundTrip = dayTrip.get().fareYen() * 2;
                    dayTrips.add(new TransportEstimate.DayTrip(day.getDayNumber(), dayCity.name(), dayCity.label(),
                            dayTrip.get().label(), roundTrip, roundTrip + dayMin, roundTrip + dayMax));
                    tripMin += roundTrip + dayMin;
                    tripMax += roundTrip + dayMax;
                }
                dayMaxFares.add(0);   // 숙소 도시 1일권은 이날 쓰지 않는다
                continue;
            }
            legs += dayLegs;
            min += dayMin;
            max += dayMax;
            dayMaxFares.add(dayMax);
        }

        TransitFare.PassPlan plan = TransitFare.bestPasses(dayMaxFares, fare);
        boolean usePass = !plan.passes().isEmpty() && plan.savings() > 0;
        int passCost = usePass ? plan.cost() : max;
        TransportEstimate.Subway subway = new TransportEstimate.Subway(legs, min, max,
                usePass ? plan.passes() : List.of(), passCost, usePass ? plan.savings() : 0,
                Math.min(min, passCost), Math.min(max, passCost));

        TransportEstimate.Airport airport = airport(trip);
        int personMin = subway.finalMinYen() + (airport == null ? 0 : airport.minYen()) + tripMin;
        int personMax = subway.finalMaxYen() + (airport == null ? 0 : airport.maxYen()) + tripMax;
        return new TransportEstimate(range(personMin, personMax, krwPerYen),
                range(personMin * travelers, personMax * travelers, krwPerYen),
                subway, airport, rentalCar, List.copyOf(days), TransportEstimate.NOTE, List.copyOf(dayTrips));
    }

    /** 하루 마지막 장소 → 숙소가 걷기엔 멀면 그 대중교통 거리, 아니면 null */
    static Integer returnDistance(ItineraryDay day, Point hotel, double walkLimitMeters, double transitDetour) {
        if (hotel == null) return null;
        return returnDistance(day, hotel.getY(), hotel.getX(), walkLimitMeters, transitDetour);
    }

    /** 하루 마지막 장소 → 돌아갈 곳(숙소, 당일치기 날은 그 도시 역)이 걷기엔 멀면 그 대중교통 거리 */
    static Integer returnDistance(ItineraryDay day, double endLat, double endLng, double walkLimitMeters, double transitDetour) {
        if (day.getStops().isEmpty()) return null;
        ItineraryStop last = day.getStops().stream().max(Comparator.comparingInt(ItineraryStop::getStopOrder)).orElseThrow();
        Point at = last.getAttraction() != null ? last.getAttraction().getLocation()
                : last.getRestaurant() != null ? last.getRestaurant().getLocation() : null;
        if (at == null) return null;
        double straight = GeoUtils.distanceMeters(at.getY(), at.getX(), endLat, endLng);
        return straight <= walkLimitMeters ? null : (int) Math.round(straight * transitDetour);
    }

    /** 공항 이동 (도착·출발, 싼 방법과 빠른 방법). 공항 정보가 없으면 null */
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

    /** 공항 ↔ 도시 이동 한 구간 */
    private static TransportEstimate.Leg leg(AirportAccess a) {
        return new TransportEstimate.Leg(a.airport().label(), a.cheapYen(), a.cheapLabel(), a.fastYen(), a.fastLabel());
    }

    /** 엔 범위 → 엔·원 범위 */
    private static TransportEstimate.Range range(int minYen, int maxYen, double krwPerYen) {
        return new TransportEstimate.Range(minYen, maxYen,
                (int) Math.round(minYen * krwPerYen), (int) Math.round(maxYen * krwPerYen));
    }
}
