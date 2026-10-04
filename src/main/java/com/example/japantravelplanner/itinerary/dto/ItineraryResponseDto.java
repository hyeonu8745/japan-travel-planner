package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DropReason;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;

/** 완성된 일정 한 벌 (화면의 타임라인, 지도, 장소 상세에 필요한 값). 엔티티를 열어둔 트랜잭션 안에서 만든다 */
public record ItineraryResponseDto(
        Long id,
        Long tripId,
        int version,
        double krwPerYen,
        LocalDateTime createdAt,
        List<DayDto> days
) {

    public record DayDto(
            int dayNumber,
            LocalDate date,
            TripZone zone,
            String zoneLabel,
            LocalTime windowStart,
            LocalTime windowEnd,
            String description,
            List<StopDto> stops,
            List<DroppedDto> dropped,
            List<String> warnings
    ) {
    }

    public record StopDto(
            int order,
            StopType type,
            String name,
            LocalTime arrival,
            LocalTime departure,
            int stayMinutes,
            int waitMinutes,
            TravelDto travel,
            Double lat,
            Double lng,
            AttractionDto attraction,
            RestaurantDto restaurant
    ) {
    }

    public record TravelDto(LegMode mode, int minutes, int distanceMeters, boolean estimated) {
    }

    public record AttractionDto(Long id, List<String> labels, Double rating, Integer reviewCount, String address) {
    }

    public record RestaurantDto(Long id, String genre, Double rating, String budget, String address,
                                boolean budgetRelaxed, boolean genreRepeated) {
    }

    public record DroppedDto(String name, DropReason reason, String reasonLabel) {
    }

    /** @param typeLabels Google type 문자열 → 화면에 보이는 한국어 이름 */
    public static ItineraryResponseDto from(Itinerary itinerary, Function<String, List<String>> typeLabels) {
        List<DayDto> days = itinerary.getDays().stream().map(day -> toDay(day, typeLabels)).toList();
        return new ItineraryResponseDto(itinerary.getId(), itinerary.getTrip().getId(), itinerary.getVersionNo(),
                itinerary.getKrwPerYen(), itinerary.getCreatedAt(), days);
    }

    private static DayDto toDay(ItineraryDay day, Function<String, List<String>> typeLabels) {
        return new DayDto(day.getDayNumber(), day.getDate(), day.getZone(), day.getZone().getLabel(),
                day.getWindowStart(), day.getWindowEnd(), day.getDescription(),
                day.getStops().stream().map(stop -> toStop(stop, typeLabels)).toList(),
                day.getDropped().stream().map(ItineraryResponseDto::toDropped).toList(),
                List.copyOf(day.getWarnings()));
    }

    private static StopDto toStop(ItineraryStop stop, Function<String, List<String>> typeLabels) {
        TravelDto travel = new TravelDto(stop.getTravelMode(), stop.getTravelMinutes(),
                stop.getTravelDistanceMeters(), stop.isTravelEstimated());

        if (stop.getType() == StopType.ATTRACTION) {
            Attraction a = stop.getAttraction();
            return new StopDto(stop.getStopOrder(), stop.getType(), a.getName(),
                    stop.getArrivalTime(), stop.getDepartureTime(), stop.getStayMinutes(), stop.getWaitMinutes(),
                    travel, lat(a.getLocation()), lng(a.getLocation()),
                    new AttractionDto(a.getId(), typeLabels.apply(a.getTypes()), a.getRating(), a.getReviewCnt(), a.getAddress()),
                    null);
        }

        Restaurant r = stop.getRestaurant();
        String budget = stop.getType() == StopType.LUNCH ? r.getBudgetLunch() : r.getBudgetDinner();
        return new StopDto(stop.getStopOrder(), stop.getType(), r.getName(),
                stop.getArrivalTime(), stop.getDepartureTime(), stop.getStayMinutes(), stop.getWaitMinutes(),
                travel, lat(r.getLocation()), lng(r.getLocation()),
                null,
                new RestaurantDto(r.getId(), r.getGenre(), r.getRatingVal(), budget, r.getAddress(),
                        stop.isBudgetRelaxed(), stop.isGenreRepeated()));
    }

    private static DroppedDto toDropped(DroppedPlace dropped) {
        return new DroppedDto(dropped.getName(), dropped.getReason(), dropped.getReason().label());
    }

    // JTS 좌표는 (경도 x, 위도 y) 순서
    private static Double lat(org.locationtech.jts.geom.Point point) {
        return point == null ? null : point.getY();
    }

    private static Double lng(org.locationtech.jts.geom.Point point) {
        return point == null ? null : point.getX();
    }
}