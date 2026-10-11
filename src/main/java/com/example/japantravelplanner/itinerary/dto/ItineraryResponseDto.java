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
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.meal.RegularHoliday;
import com.example.japantravelplanner.itinerary.naming.GenreLabels;
import com.example.japantravelplanner.itinerary.reason.ReasonContext;
import com.example.japantravelplanner.itinerary.reason.StopReason;
import com.example.japantravelplanner.itinerary.reason.StopReasons;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 완성된 일정 한 벌 (화면의 타임라인, 지도, 장소 상세에 필요한 값). 엔티티를 열어둔 트랜잭션 안에서 만든다 */
public record ItineraryResponseDto(
        Long id,
        Long tripId,
        int version,
        ItinerarySource source,
        String changeNote,
        double krwPerYen,
        LocalDateTime createdAt,
        AccommodationDto accommodation,
        List<DayDto> days

) {

    /** 숙소 (지도의 숙소 핀, 하루의 출발점). 좌표는 일정을 만들 때 주소로 찾아 저장한 값 */
    public record AccommodationDto(String name, String address, Double lat, Double lng) {
    }

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
            List<String> warnings,
            List<TripZone> zones,     // 10-11 추가: 그날 실제로 도는 지역 (중심 지역 먼저, 관광지 위치로 계산). 하루에 두 지역일 수 있다
            String zonesLabel         // zones를 한 줄로 ("아사쿠사·우에노 + 아키하바라·간다")
    ) {
    }

    /**
     * @param reasons 이곳을 고른 이유 (화면의 칩, 최대 3개, 없으면 빈 목록). 서버가 고른 기준에서 매번 계산한다
     */
    public record StopDto(
            Long id,
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
            RestaurantDto restaurant,
            List<AlternativeDto> alternatives,
            List<StopReason> reasons,
            String nameKo             // 10-11 추가: 한국어 이름 (없으면 null → 화면은 name). 화면은 "한국어 + 작게 원래 이름"
    ) {

        StopDto withNameKo(String value) {
            return new StopDto(id, order, type, name, arrival, departure, stayMinutes, waitMinutes, travel, lat, lng,
                    attraction, restaurant, alternatives, reasons, value);
        }

        StopDto withAlternatives(List<AlternativeDto> value) {
            return new StopDto(id, order, type, name, arrival, departure, stayMinutes, waitMinutes, travel, lat, lng,
                    attraction, restaurant, value, reasons, nameKo);
        }
    }

    public record TravelDto(LegMode mode, int minutes, int distanceMeters, boolean estimated) {
    }

    public record AttractionDto(Long id, List<String> labels, Double rating, Integer reviewCount, String address,
                                String ward, String placeId) {
    }

    /**
     * @param holiday         정기 휴일 (타베로그 원본 문구 그대로. 임시 휴무는 반영되지 않음)
     * @param closedOnVisitDay 방문하는 날이 정기 휴일 요일인지 (문구가 애매하면 false. 공휴일은 판단하지 않음)
     */
    public record RestaurantDto(Long id, String genre, Double rating, String budget, String address,
                                boolean budgetRelaxed, boolean genreRepeated,
                                Integer reviewCount, String nearestStation, Integer nearestDistance, String holiday,
                                boolean closedOnVisitDay,
                                List<String> genreLabels) {   // 10-11 추가: 장르 한국어 이름 (GenreLabels, 예: [츠케멘, 라멘])
    }

    /** 고른 식당 대신 선택할 수 있는 다음 후보 (식당 항목에만 있고, 관광지 항목은 빈 목록) */
    public record AlternativeDto(Long id, String name, String genre, Double rating, String budget, String address,
                                 Double lat, Double lng, String holiday, boolean closedOnVisitDay,
                                 String nameKo, List<String> genreLabels) {   // 10-11 추가

        AlternativeDto withNameKo(String value) {
            return new AlternativeDto(id, name, genre, rating, budget, address, lat, lng, holiday, closedOnVisitDay,
                    value, genreLabels);
        }
    }

    public record DroppedDto(String name, DropReason reason, String reasonLabel) {
    }

    /**
     * 한국어 이름이 비어 있던 항목에 새로 만든 이름을 채운 복사본 (KoreanNameService.fill).
     * 이미 이름이 있는 항목은 그대로 둔다
     * @param attractionNames 관광지 id → 한국어 이름
     * @param restaurantNames 식당 id → 한국어 이름 (고른 식당과 다음 후보 모두)
     */
    public ItineraryResponseDto withKoreanNames(Map<Long, String> attractionNames, Map<Long, String> restaurantNames) {
        List<DayDto> newDays = days.stream().map(day -> new DayDto(day.dayNumber(), day.date(), day.zone(),
                day.zoneLabel(), day.windowStart(), day.windowEnd(), day.description(),
                day.stops().stream().map(stop -> {
                    StopDto s = stop;
                    if (s.nameKo() == null) {
                        String found = s.attraction() != null ? attractionNames.get(s.attraction().id())
                                : s.restaurant() != null ? restaurantNames.get(s.restaurant().id()) : null;
                        if (found != null) s = s.withNameKo(found);
                    }
                    return s.withAlternatives(s.alternatives().stream()
                            .map(alt -> alt.nameKo() == null && restaurantNames.containsKey(alt.id())
                                    ? alt.withNameKo(restaurantNames.get(alt.id())) : alt)
                            .toList());
                }).toList(),
                day.dropped(), day.warnings(), day.zones(), day.zonesLabel())).toList();
        return new ItineraryResponseDto(id, tripId, version, source, changeNote, krwPerYen, createdAt, accommodation, newDays);
    }

    /** 식당 대안 후보를 조회하지 않는 형태 (대안은 빈 목록) */
    public static ItineraryResponseDto from(Itinerary itinerary, Function<String, List<String>> typeLabels) {
        return from(itinerary, typeLabels, Map.of());
    }

    /**
     * @param typeLabels   Google type 문자열 → 화면에 보이는 한국어 이름
     * @param alternatives 식당 id → 식당 (대안 후보를 풀어서 보여주기 위해 미리 한꺼번에 조회한 것)
     */
    public static ItineraryResponseDto from(Itinerary itinerary, Function<String, List<String>> typeLabels,
                                            Map<Long, Restaurant> alternatives) {
        return from(itinerary, typeLabels, alternatives, ReasonContext.NONE);
    }

    /**
     * @param reasons 고른 이유를 계산할 여행 조건 (관심사, 음식 조건). null이면 관심사·좋아하는 음식 이유 없이
     */
    public static ItineraryResponseDto from(Itinerary itinerary, Function<String, List<String>> typeLabels,
                                            Map<Long, Restaurant> alternatives, ReasonContext reasons) {
        ReasonContext context = ReasonContext.orNone(reasons);
        List<DayDto> days = itinerary.getDays().stream()
                .map(day -> toDay(day, typeLabels, alternatives, context)).toList();
        Trip trip = itinerary.getTrip();
        AccommodationDto accommodation = new AccommodationDto(trip.getAccommodationName(),
                trip.getAccommodationAddress(), lat(trip.getAccommodationLocation()), lng(trip.getAccommodationLocation()));
        return new ItineraryResponseDto(itinerary.getId(), trip.getId(), itinerary.getVersionNo(), itinerary.getSource(),
                itinerary.getChangeNote(), itinerary.getKrwPerYen(), itinerary.getCreatedAt(), accommodation, days);
    }

    /** 일정 전체에서 대안 후보로 쓰인 식당 id (한 번에 조회하려고 모은다) */
    public static Set<Long> alternativeRestaurantIds(Itinerary itinerary) {
        Set<Long> ids = new LinkedHashSet<>();
        itinerary.getDays().forEach(day -> day.getStops().forEach(stop -> ids.addAll(stop.getAlternativeRestaurantIds())));
        return ids;
    }

    private static DayDto toDay(ItineraryDay day, Function<String, List<String>> typeLabels,
                                Map<Long, Restaurant> alternatives, ReasonContext reasons) {
        return new DayDto(day.getDayNumber(), day.getDate(), day.getZone(), day.getZone().getLabel(),
                day.getWindowStart(), day.getWindowEnd(), day.getDescription(),
                day.getStops().stream().map(stop -> toStop(stop, day.getDate(), typeLabels, alternatives, reasons)).toList(),
                day.getDropped().stream().map(ItineraryResponseDto::toDropped).toList(),
                List.copyOf(day.getWarnings()), zonesOf(day), TripZone.labelOf(zonesOf(day)));
    }

    /** 그날 도는 지역: 관광지 위치의 지역 (중심 지역에 관광지가 있으면 맨 앞, 관광지가 없으면 중심 지역) */
    static List<TripZone> zonesOf(ItineraryDay day) {
        List<TripZone> stopZones = new java.util.ArrayList<>();
        day.getStops().stream()
                .filter(stop -> stop.getType() == StopType.ATTRACTION && stop.getAttraction() != null
                        && stop.getAttraction().getLocation() != null)
                .forEach(stop -> TripZone.nearest(stop.getAttraction().getLocation().getY(),
                        stop.getAttraction().getLocation().getX()).ifPresent(stopZones::add));
        return TripZone.visited(day.getZone(), stopZones);
    }

    private static StopDto toStop(ItineraryStop stop, LocalDate date, Function<String, List<String>> typeLabels,
                                  Map<Long, Restaurant> alternatives, ReasonContext reasons) {
        TravelDto travel = new TravelDto(stop.getTravelMode(), stop.getTravelMinutes(),
                stop.getTravelDistanceMeters(), stop.isTravelEstimated());

        if (stop.getType() == StopType.ATTRACTION) {
            Attraction a = stop.getAttraction();
            return new StopDto(stop.getId(), stop.getStopOrder(), stop.getType(), a.getName(),
                    stop.getArrivalTime(), stop.getDepartureTime(), stop.getStayMinutes(), stop.getWaitMinutes(),
                    travel, lat(a.getLocation()), lng(a.getLocation()),
                    new AttractionDto(a.getId(), typeLabels.apply(a.getTypes()), a.getRating(), a.getReviewCnt(),
                            a.getAddress(), a.getWard(), a.getPlaceId()),
                    null, List.of(), StopReasons.of(stop, reasons), a.getNameKo());
        }

        Restaurant r = stop.getRestaurant();
        return new StopDto(stop.getId(), stop.getStopOrder(), stop.getType(), r.getName(),
                stop.getArrivalTime(), stop.getDepartureTime(), stop.getStayMinutes(), stop.getWaitMinutes(),
                travel, lat(r.getLocation()), lng(r.getLocation()),
                null,
                new RestaurantDto(r.getId(), r.getGenre(), r.getRatingVal(), budgetOf(r, stop.getType()), r.getAddress(),
                        stop.isBudgetRelaxed(), stop.isGenreRepeated(),
                        r.getReviewCnt(), r.getNearestStation(), r.getNearestDistance(), r.getHoliday(),
                        RegularHoliday.isClosedOn(r.getHoliday(), date), GenreLabels.of(r.getGenre())),
                toAlternatives(stop, date, alternatives), StopReasons.of(stop, reasons), r.getNameKo());
    }

    // 저장된 순서(점수 높은 순)를 지키고, 그 사이에 사라진 식당은 건너뛴다
    private static List<AlternativeDto> toAlternatives(ItineraryStop stop, LocalDate date, Map<Long, Restaurant> alternatives) {
        return stop.getAlternativeRestaurantIds().stream()
                .map(alternatives::get)
                .filter(java.util.Objects::nonNull)
                .map(r -> new AlternativeDto(r.getId(), r.getName(), r.getGenre(), r.getRatingVal(),
                        budgetOf(r, stop.getType()), r.getAddress(), lat(r.getLocation()), lng(r.getLocation()),
                        r.getHoliday(), RegularHoliday.isClosedOn(r.getHoliday(), date),
                        r.getNameKo(), GenreLabels.of(r.getGenre())))
                .toList();
    }

    // 점심이면 점심 예산, 저녁이면 저녁 예산
    private static String budgetOf(Restaurant r, StopType type) {
        return type == StopType.LUNCH ? r.getBudgetLunch() : r.getBudgetDinner();
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