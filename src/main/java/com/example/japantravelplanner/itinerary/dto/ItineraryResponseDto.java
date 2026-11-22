package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.itinerary.transit.TransitPlan;
import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.meal.RegularHoliday;
import com.example.japantravelplanner.itinerary.naming.GenreLabels;
import com.example.japantravelplanner.itinerary.planner.AirportPlan;
import com.example.japantravelplanner.itinerary.planner.DropReason;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.reason.ReasonContext;
import com.example.japantravelplanner.itinerary.reason.StopReason;
import com.example.japantravelplanner.itinerary.reason.StopReasons;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Trip;

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
        List<DayDto> days,
        ExchangeRateDto exchangeRate    // 이 일정의 환율 기준일·출처 (예전 일정은 date·source가 null)

) {

    /**
     * @param krwPerYen   1엔당 원 (위 krwPerYen과 같은 값)
     * @param date        환율 기준일 (null이면 고정값 또는 예전 일정)
     * @param source      KOREAEXIM / FRANKFURTER / FIXED, 예전 일정은 null
     * @param sourceLabel 화면 표시용 ("한국수출입은행", "Frankfurter(유럽중앙은행 등)", "고정 환율")
     */
    public record ExchangeRateDto(double krwPerYen, LocalDate date, String source, String sourceLabel) {

        /** 환율 응답 (출처 이름 포함) */
        static ExchangeRateDto of(double krwPerYen, LocalDate date, String source) {
            String label = source == null ? "고정 환율" : switch (source) {
                case "KOREAEXIM" -> "한국수출입은행";
                case "FRANKFURTER" -> "Frankfurter(유럽중앙은행 등)";
                default -> "고정 환율";
            };
            return new ExchangeRateDto(krwPerYen, date, source, label);
        }
    }

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
            List<TripZone> zones,     // 그날 실제로 도는 지역 (중심 지역 먼저, 관광지 위치로 계산). 하루에 두 지역일 수 있다
            String zonesLabel,        // zones를 한 줄로 ("아사쿠사·우에노 + 아키하바라·간다")
            List<TransferDto> transfers   // 공항 이동 (첫날·마지막 날, 비행기 시각이 있을 때만. 없으면 빈 목록)
    ) {
    }

    /**
     * 공항 이동 한 단계 (일정 항목이 아니라 서버가 여행 조건으로 계산해 붙이는 안내).
     *
     * @param kind     FLIGHT_ARRIVAL, AIRPORT_TO_HOTEL, HOTEL_DROP / CHECKOUT, HOTEL_PICKUP, HOTEL_TO_AIRPORT, FLIGHT_DEPARTURE / DAY_TRIP_GO, DAY_TRIP_BACK (당일치기 날, 11-22)
     * @param position BEFORE = 그날 첫 장소 앞에, AFTER = 마지막 장소 뒤에 보여준다
     */
    public record TransferDto(LocalTime time, String kind, String label, String position) {
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
            String nameKo             // 한국어 이름 (없으면 null → 화면은 name). 화면은 "한국어 + 작게 원래 이름"
    ) {

        /** 한국어 이름만 바꾼 복사본 */
        StopDto withNameKo(String value) {
            return new StopDto(id, order, type, name, arrival, departure, stayMinutes, waitMinutes, travel, lat, lng,
                    attraction, restaurant, alternatives, reasons, value);
        }

        /** 식당 대안 목록만 바꾼 복사본 */
        StopDto withAlternatives(List<AlternativeDto> value) {
            return new StopDto(id, order, type, name, arrival, departure, stayMinutes, waitMinutes, travel, lat, lng,
                    attraction, restaurant, value, reasons, nameKo);
        }
    }

    /**
     * 앞 장소 → 이 장소 이동
     * @param route   대중교통 구간이면 대강의 경로 (가까운 역·탈 노선, 정차역 순서로 찾은 참고용, 11-22). 못 찾으면 null
     * @param mapsUrl Google 지도 길찾기 링크 (앞 장소 좌표가 있을 때). 정확한 경로·시간은 여기서 확인
     */
    public record TravelDto(LegMode mode, int minutes, int distanceMeters, boolean estimated,
                            TransitPlan route, String mapsUrl) {
        /** 경로 안내 없이 (교체 후보 미리보기 등) */
        public TravelDto(LegMode mode, int minutes, int distanceMeters, boolean estimated) {
            this(mode, minutes, distanceMeters, estimated, null, null);
        }
    }

    /** 구간 대중교통 경로 찾기 (TransitGuide.plan). 응답을 만들 때만 쓰고 저장하지 않는다 */
    @FunctionalInterface
    public interface TransitLookup {
        java.util.Optional<TransitPlan> plan(double fromLat, double fromLng, double toLat, double toLng);

        TransitLookup NONE = (a, b, c, d) -> java.util.Optional.empty();
    }

    /**
     * @param rating      Google 평점. 확장 데이터 관광지는 없을 수 있다 (null)
     * @param mustSee     그 도시에서 꼭 가는 대표 관광지 (11-18 추가)
     * @param photo       위키미디어 대표 사진 (없으면 null → 화면은 Google 사진). 작가·라이선스를 사진과 함께 표시해야 한다 (11-18 추가)
     * @param historyNote 역사 배경 안내 한 줄 (민감하지만 대표 관광지라 빼지 않은 곳, 없으면 null, 11-18 추가)
     */
    public record AttractionDto(Long id, List<String> labels, Double rating, Integer reviewCount, String address,
                                String ward, String placeId, boolean mustSee, PhotoDto photo, String historyNote) {

        /** 예전 필드만 (사진·꼭 가는 곳 없이) */
        public AttractionDto(Long id, List<String> labels, Double rating, Integer reviewCount, String address,
                             String ward, String placeId) {
            this(id, labels, rating, reviewCount, address, ward, placeId, false, null, null);
        }
    }

    /**
     * 관광지 대표 사진 (위키미디어 공용).
     * @param url        썸네일 주소
     * @param artist     작가 (HTML 이 섞여 있을 수 있어 글자만 보여 줄 것)
     * @param license    라이선스 이름 (예: CC BY-SA 4.0)
     * @param licenseUrl 라이선스 주소
     * @param credit     출처 표시 문구
     */
    public record PhotoDto(String url, Integer width, Integer height, String artist, String license,
                           String licenseUrl, String credit) {

        /** 엔티티 사진 → 응답 (주소가 없으면 null) */
        static PhotoDto of(com.example.japantravelplanner.attraction.entity.AttractionPhoto photo) {
            if (photo == null || photo.getThumb() == null || photo.getThumb().isBlank()) return null;
            return new PhotoDto(photo.getThumb(), photo.getWidth(), photo.getHeight(), photo.getArtist(),
                    photo.getLicense(), photo.getLicenseUrl(), photo.getCredit());
        }
    }

    /**
     * @param holiday         정기 휴일 (타베로그 원본 문구 그대로. 임시 휴무는 반영되지 않음)
     * @param closedOnVisitDay 방문하는 날이 정기 휴일 요일인지 (문구가 애매하면 false. 공휴일은 판단하지 않음)
     */
    public record RestaurantDto(Long id, String genre, Double rating, String budget, String address,
                                boolean budgetRelaxed, boolean genreRepeated,
                                Integer reviewCount, String nearestStation, Integer nearestDistance, String holiday,
                                boolean closedOnVisitDay,
                                List<String> genreLabels) {   // 장르 한국어 이름 (GenreLabels, 예: [츠케멘, 라멘])
    }

    /** 고른 식당 대신 선택할 수 있는 다음 후보 (식당 항목에만 있고, 관광지 항목은 빈 목록) */
    public record AlternativeDto(Long id, String name, String genre, Double rating, String budget, String address,
                                 Double lat, Double lng, String holiday, boolean closedOnVisitDay,
                                 String nameKo, List<String> genreLabels) {   // 추가

        /** 한국어 이름만 바꾼 복사본 */
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
                day.dropped(), day.warnings(), day.zones(), day.zonesLabel(), day.transfers())).toList();
        return new ItineraryResponseDto(id, tripId, version, source, changeNote, krwPerYen, createdAt, accommodation, newDays,
                exchangeRate);
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
        return from(itinerary, typeLabels, alternatives, reasons, TransitLookup.NONE);
    }

    /** @param transit 대중교통 구간의 대강의 경로 찾기 (11-22) */
    public static ItineraryResponseDto from(Itinerary itinerary, Function<String, List<String>> typeLabels,
                                            Map<Long, Restaurant> alternatives, ReasonContext reasons,
                                            TransitLookup transit) {
        ReasonContext context = ReasonContext.orNone(reasons);
        Trip trip = itinerary.getTrip();
        int lastDay = itinerary.getDays().stream().mapToInt(ItineraryDay::getDayNumber).max().orElse(1);
        TransitLookup lookup = transit == null ? TransitLookup.NONE : transit;
        List<DayDto> days = itinerary.getDays().stream()
                .map(day -> toDay(day, typeLabels, alternatives, context, transfersOf(trip, day, lastDay),
                        travels(trip, day, lookup))).toList();
        AccommodationDto accommodation = new AccommodationDto(trip.getAccommodationName(),
                trip.getAccommodationAddress(), GeoUtils.lat(trip.getAccommodationLocation()), GeoUtils.lng(trip.getAccommodationLocation()));
        return new ItineraryResponseDto(itinerary.getId(), trip.getId(), itinerary.getVersionNo(), itinerary.getSource(),
                itinerary.getChangeNote(), itinerary.getKrwPerYen(), itinerary.getCreatedAt(), accommodation, days,
                ExchangeRateDto.of(itinerary.getKrwPerYen(), itinerary.getExchangeRateDate(), itinerary.getExchangeRateSource()));
    }

    /** 일정 전체에서 대안 후보로 쓰인 식당 id (한 번에 조회하려고 모은다) */
    public static Set<Long> alternativeRestaurantIds(Itinerary itinerary) {
        Set<Long> ids = new LinkedHashSet<>();
        itinerary.getDays().forEach(day -> day.getStops().forEach(stop -> ids.addAll(stop.getAlternativeRestaurantIds())));
        return ids;
    }

    /** 첫날은 공항 → 숙소, 마지막 날은 체크아웃 … 숙소 → 공항 (하루짜리 여행이면 둘 다), 당일치기 날은 숙소 ↔ 그 도시 */
    static List<TransferDto> transfersOf(Trip trip, ItineraryDay day, int lastDay) {
        if (trip == null) return List.of();
        List<AirportPlan.Step> steps = new java.util.ArrayList<>();
        if (day.getDayNumber() == 1) steps.addAll(AirportPlan.arrivalSteps(trip));
        if (day.getDayNumber() == lastDay) steps.addAll(AirportPlan.departureSteps(trip, day.getWindowStart()));
        // 당일치기 날: 숙소 → 역 → 그 도시 / 마지막 장소 → 역 → 숙소 (11-22)
        java.time.LocalTime lastDeparture = day.getStops().stream()
                .max(java.util.Comparator.comparingInt(ItineraryStop::getStopOrder))
                .map(ItineraryStop::getDepartureTime).orElse(day.getWindowEnd());
        steps.addAll(com.example.japantravelplanner.itinerary.planner.DayTrips.steps(trip, day.getZone(), day.getWindowStart(), lastDeparture));
        return steps.stream().map(st -> new TransferDto(st.time(), st.kind(), st.label(), st.position())).toList();
    }

    /**
     * 그날 항목마다 이동 (앞 장소 → 이 장소). 첫 항목의 앞 장소는 숙소, 당일치기 날은 그 도시 도착역.
     * 대중교통 구간은 대강의 경로, 모든 구간에 Google 지도 길찾기 링크
     */
    static Map<ItineraryStop, TravelDto> travels(Trip trip, ItineraryDay day, TransitLookup transit) {
        Map<ItineraryStop, TravelDto> result = new java.util.IdentityHashMap<>();
        com.example.japantravelplanner.itinerary.travel.TravelPoint previous = null;
        if (trip != null && trip.getAccommodationLocation() != null) {
            previous = com.example.japantravelplanner.itinerary.planner.DayTrips.startPoint(trip, day.getZone(),
                    com.example.japantravelplanner.itinerary.travel.TravelPoint.of(trip.getAccommodationName(), trip.getAccommodationLocation()));
        }
        List<ItineraryStop> ordered = day.getStops().stream()
                .sorted(java.util.Comparator.comparingInt(ItineraryStop::getStopOrder)).toList();
        for (ItineraryStop stop : ordered) {
            org.locationtech.jts.geom.Point at = stop.getAttraction() != null ? stop.getAttraction().getLocation()
                    : stop.getRestaurant() != null ? stop.getRestaurant().getLocation() : null;
            TransitPlan route = null;
            String url = null;
            if (previous != null && at != null) {
                url = mapsUrl(previous.lat(), previous.lng(), at.getY(), at.getX(), stop.getTravelMode());
                if (stop.getTravelMode() == LegMode.TRANSIT) {
                    route = transit.plan(previous.lat(), previous.lng(), at.getY(), at.getX()).orElse(null);
                }
            }
            result.put(stop, new TravelDto(stop.getTravelMode(), stop.getTravelMinutes(),
                    stop.getTravelDistanceMeters(), stop.isTravelEstimated(), route, url));
            if (at != null) previous = new com.example.japantravelplanner.itinerary.travel.TravelPoint(null, at.getY(), at.getX());
        }
        return result;
    }

    /** Google 지도 길찾기 링크 (대중교통·도보·자동차) */
    static String mapsUrl(double fromLat, double fromLng, double toLat, double toLng, LegMode mode) {
        String travelMode = mode == LegMode.WALK ? "walking" : mode == LegMode.DRIVE ? "driving" : "transit";
        return String.format(java.util.Locale.ROOT,
                "https://www.google.com/maps/dir/?api=1&origin=%.6f,%.6f&destination=%.6f,%.6f&travelmode=%s",
                fromLat, fromLng, toLat, toLng, travelMode);
    }

    /** 하루 엔티티 → 응답 (항목, 지역, 빠진 장소, 안내, 공항 이동) */
    private static DayDto toDay(ItineraryDay day, Function<String, List<String>> typeLabels,
                                Map<Long, Restaurant> alternatives, ReasonContext reasons, List<TransferDto> transfers,
                                Map<ItineraryStop, TravelDto> travels) {
        return new DayDto(day.getDayNumber(), day.getDate(), day.getZone(), day.getZone().getLabel(),
                day.getWindowStart(), day.getWindowEnd(), day.getDescription(),
                day.getStops().stream().map(stop -> toStop(stop, day.getDate(), typeLabels, alternatives, reasons,
                        travels.get(stop))).toList(),
                day.getDropped().stream().map(ItineraryResponseDto::toDropped).toList(),
                List.copyOf(day.getWarnings()), zonesOf(day), TripZone.labelOf(zonesOf(day)), transfers);
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

    /** 항목 엔티티 → 응답 (관광지면 종류·이유, 식당이면 장르·예산·대안·이유, 이동) */
    private static StopDto toStop(ItineraryStop stop, LocalDate date, Function<String, List<String>> typeLabels,
                                  Map<Long, Restaurant> alternatives, ReasonContext reasons, TravelDto computed) {
        TravelDto travel = computed != null ? computed : new TravelDto(stop.getTravelMode(), stop.getTravelMinutes(),
                stop.getTravelDistanceMeters(), stop.isTravelEstimated());

        if (stop.getType() == StopType.ATTRACTION) {
            Attraction a = stop.getAttraction();
            return new StopDto(stop.getId(), stop.getStopOrder(), stop.getType(), a.getName(),
                    stop.getArrivalTime(), stop.getDepartureTime(), stop.getStayMinutes(), stop.getWaitMinutes(),
                    travel, GeoUtils.lat(a.getLocation()), GeoUtils.lng(a.getLocation()),
                    new AttractionDto(a.getId(), typeLabels.apply(a.typesOrKind()), a.getRating(), a.getReviewCnt(),
                            a.getAddress(), a.getWard(), a.getPlaceId(), a.isMustSee(), PhotoDto.of(a.getPhoto()),
                            a.getHistoryNote()),
                    null, List.of(), StopReasons.of(stop, reasons), a.getNameKo());
        }

        Restaurant r = stop.getRestaurant();
        return new StopDto(stop.getId(), stop.getStopOrder(), stop.getType(), r.getName(),
                stop.getArrivalTime(), stop.getDepartureTime(), stop.getStayMinutes(), stop.getWaitMinutes(),
                travel, GeoUtils.lat(r.getLocation()), GeoUtils.lng(r.getLocation()),
                null,
                new RestaurantDto(r.getId(), r.genreOrBrand(), r.getRatingVal(), budgetOf(r, stop.getType()), r.getAddress(),
                        stop.isBudgetRelaxed(), stop.isGenreRepeated(),
                        r.getReviewCnt(), r.getNearestStation(), r.getNearestDistance(), r.getHoliday(),
                        RegularHoliday.isClosedOn(r.getHoliday(), date), GenreLabels.of(r.genreOrBrand())),
                toAlternatives(stop, date, alternatives), StopReasons.of(stop, reasons), r.getNameKo());
    }

    // 저장된 순서(점수 높은 순)를 지키고, 그 사이에 사라진 식당은 건너뛴다
    private static List<AlternativeDto> toAlternatives(ItineraryStop stop, LocalDate date, Map<Long, Restaurant> alternatives) {
        return stop.getAlternativeRestaurantIds().stream()
                .map(alternatives::get)
                .filter(java.util.Objects::nonNull)
                .map(r -> new AlternativeDto(r.getId(), r.getName(), r.genreOrBrand(), r.getRatingVal(),
                        budgetOf(r, stop.getType()), r.getAddress(), GeoUtils.lat(r.getLocation()), GeoUtils.lng(r.getLocation()),
                        r.getHoliday(), RegularHoliday.isClosedOn(r.getHoliday(), date),
                        r.getNameKo(), GenreLabels.of(r.genreOrBrand())))
                .toList();
    }

    // 점심이면 점심 예산, 저녁이면 저녁 예산, 쉬는 시간 카페는 낮 예산 → 없으면 저녁 예산.
    // 체인점은 예산 글자가 없어서 브랜드 예산을 같은 모양으로 ("￥500～￥1,000")
    private static String budgetOf(Restaurant r, StopType type) {
        if (r.isChain() && r.getBudgetLunch() == null && r.getBudgetDinner() == null) {
            Integer min = r.getBrand().getBudgetMin();
            Integer max = r.getBrand().getBudgetMax();
            if (min == null || max == null) return null;
            return String.format(java.util.Locale.ROOT, "￥%,d～￥%,d", min, max);
        }
        if (type == StopType.CAFE) {
            return r.getBudgetLunch() != null && !r.getBudgetLunch().isBlank() && !"-".equals(r.getBudgetLunch().strip())
                    ? r.getBudgetLunch() : r.getBudgetDinner();
        }
        return type == StopType.LUNCH ? r.getBudgetLunch() : r.getBudgetDinner();
    }

    /** 빠진 장소 → 응답 (이유 코드와 한국어 설명) */
    private static DroppedDto toDropped(DroppedPlace dropped) {
        return new DroppedDto(dropped.getName(), dropped.getReason(), dropped.getReason().label());
    }

}
