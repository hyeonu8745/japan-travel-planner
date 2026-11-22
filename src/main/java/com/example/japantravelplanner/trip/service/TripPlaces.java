package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.BadRequestException;
import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.entity.Airport;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.DayTripRoute;
import com.example.japantravelplanner.trip.region.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 여행 조건의 장소 정하기 (11-22 확장 5단계): 기준 도시(숙소 도시)·권역·당일치기 도시, 공항이 그 권역인지.
 * 여행을 만들 때와 고칠 때 같은 규칙을 쓴다. 일정을 만들 때 숙소 좌표로 도시를 다시 정할 수 있다 (AccommodationLocator).
 *
 * - 숙소 이름·주소에 도시 이름이 있으면 그 도시 (City.guess)
 * - 없으면 고른 권역의 대표 도시(그 권역 첫 도시), 권역도 없으면 지금 도시(새 여행은 도쿄)
 * - 고른 권역과 숙소 도시의 권역이 다르면 400
 * - 공항은 그 권역의 공항만, 당일치기 도시는 그 도시에서 갈 수 있는 곳만 (DayTripRoute), 같은 도시는 한 번
 *
 * @param city       기준 도시
 * @param dayTrips   당일치기 도시 (요청 순서)
 */
public record TripPlaces(City city, List<City> dayTrips) {

    /** 권역 */
    public Region region() {
        return city.region();
    }

    /**
     * @param current 지금 기준 도시 (새 여행이면 null)
     * @throws BadRequestException 권역·공항·당일치기 도시가 숙소 도시와 맞지 않을 때
     */
    public static TripPlaces resolve(TripRequestDto request, City current) {
        return resolve(request, current, List.of());
    }

    /**
     * @param currentDayTrips 지금 당일치기 도시. 요청에 dayTripCities 가 없으면(null) 이것을 그대로 두되
     *                        숙소 도시가 바뀌어 갈 수 없게 된 도시는 조용히 뺀다 (빈 목록을 보내면 모두 지운다)
     */
    public static TripPlaces resolve(TripRequestDto request, City current, List<City> currentDayTrips) {
        Region requested = regionOf(request.getRegion()).orElse(null);
        Optional<City> guessed = City.guess(request.getAccommodationName(), request.getAccommodationAddress());

        City city;
        if (guessed.isPresent()) {
            city = guessed.get();
            if (requested != null && city.region() != requested) {
                throw new BadRequestException("숙소 주소의 도시(" + city.label() + ")가 고른 권역(" + requested.label()
                        + ")에 없어요. 권역이나 숙소를 확인해주세요.");
            }
        } else if (requested != null) {
            city = current != null && current.region() == requested ? current : City.inRegion(requested).get(0);
        } else {
            city = current == null ? City.TOKYO : current;
        }

        checkAirport(request.getArrivalAirport(), city.region());
        checkAirport(request.getDepartureAirport(), city.region());
        if (request.getDayTripCities() == null) {
            City base = city;
            List<City> kept = currentDayTrips.stream()
                    .filter(c -> DayTripRoute.between(base, c).isPresent()).distinct().toList();
            return new TripPlaces(city, kept);
        }
        return new TripPlaces(city, dayTripsOf(request.getDayTripCities(), city));
    }

    /** 권역 이름 → 권역 (비어 있거나 모르면 empty, 모르는 이름은 요청 검증에서 먼저 막는다) */
    static Optional<Region> regionOf(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String c = code.strip().toUpperCase(Locale.ROOT);
        for (Region r : Region.values()) if (r.name().equals(c)) return Optional.of(r);
        return Optional.empty();
    }

    /** 공항이 여행 권역의 공항인지 (비어 있으면 통과) */
    private static void checkAirport(String code, Region region) {
        Airport.of(code).filter(a -> a.region() != region).ifPresent(a -> {
            String allowed = Airport.inRegion(region).stream()
                    .map(x -> x.label() + "(" + x.name() + ")").collect(Collectors.joining(", "));
            throw new BadRequestException(region.label() + " 여행은 " + allowed + " 중에서 공항을 골라주세요.");
        });
    }

    /** 당일치기 도시: 같은 도시·숙소 도시는 빼고, 갈 수 없는 도시면 400 */
    private static List<City> dayTripsOf(List<String> codes, City base) {
        List<City> result = new ArrayList<>();
        if (codes == null) return result;
        for (String code : codes) {
            City target = City.of(code).orElse(null);
            if (target == null || target == base || result.contains(target)) continue;
            if (DayTripRoute.between(base, target).isEmpty()) {
                List<City> reachable = DayTripRoute.targets(base);
                throw new BadRequestException(reachable.isEmpty()
                        ? base.label() + "에서는 당일치기로 다녀올 도시가 없어요."
                        : base.label() + "에서 당일치기로 갈 수 있는 도시는 "
                          + reachable.stream().map(City::label).collect(Collectors.joining(", ")) + "예요.");
            }
            result.add(target);
        }
        return result;
    }
}
