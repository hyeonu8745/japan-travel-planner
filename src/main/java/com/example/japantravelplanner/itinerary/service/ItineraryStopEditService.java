package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.AddAttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto.CandidateDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DayScheduler;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimeItem;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.Retimed;
import com.example.japantravelplanner.itinerary.planner.StayTimes;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.planner.TravelOptions;
import com.example.japantravelplanner.itinerary.reason.ReasonContextLoader;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.repository.ItineraryStopRepository;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.itinerary.travel.TravelMatrix;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import com.example.japantravelplanner.trip.service.TripAccess;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 하루 일정의 항목을 지우고, 순서를 바꾸고, 관광지를 끼워 넣는다.
 *
 * 교체와 같은 방식이다. 일정 전체를 복사해서 새 버전(EDITED)으로 저장하고, 바뀐 날만 정해진 순서대로 시각을 다시
 * 계산한다. 시간이 넘쳐도 항목을 빼지 않고 안내 문장으로만 알린다 (사용자가 고른 일정을 서버가 줄이지 않는다).
 * AI를 부르지 않으므로 하루 생성 횟수 제한에 들어가지 않는다. 바뀐 날의 AI 설명은 장소·순서가 달라져서 비운다.
 *
 * 관광지 추가 후보는 관광지 교체 후보와 같은 기준이다 (ItineraryAttractionEditService와 겹치는 작은 도우미가 있다).
 */
@Slf4j
@Service
public class ItineraryStopEditService {

    /** 추가 후보로 보여주는 관광지 수 */
    static final int CANDIDATE_LIMIT = 10;

    private final TripRepository tripRepository;
    private final TripPreferenceRepository preferenceRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryStopRepository stopRepository;
    private final AttractionRepository attractionRepository;
    private final RestaurantRepository restaurantRepository;
    private final AccommodationLocator accommodationLocator;
    private final AttractionScorer attractionScorer;
    private final TravelTimeService travelTimeService;
    private final TravelProperties travelProperties;
    private final DayScheduler dayScheduler;
    private final PlaceTypeLabels typeLabels;
    private final ItineraryVersions versions;

    public ItineraryStopEditService(TripRepository tripRepository,
                                    TripPreferenceRepository preferenceRepository,
                                    ItineraryRepository itineraryRepository,
                                    ItineraryStopRepository stopRepository,
                                    AttractionRepository attractionRepository,
                                    RestaurantRepository restaurantRepository,
                                    AccommodationLocator accommodationLocator,
                                    AttractionScorer attractionScorer,
                                    TravelTimeService travelTimeService,
                                    TravelProperties travelProperties,
                                    DayScheduler dayScheduler,
                                    PlaceTypeLabels typeLabels,
                                    GenerationLock lock,
                                    ReasonContextLoader reasonContextLoader,
                                    PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.preferenceRepository = preferenceRepository;
        this.itineraryRepository = itineraryRepository;
        this.stopRepository = stopRepository;
        this.attractionRepository = attractionRepository;
        this.restaurantRepository = restaurantRepository;
        this.accommodationLocator = accommodationLocator;
        this.attractionScorer = attractionScorer;
        this.travelTimeService = travelTimeService;
        this.travelProperties = travelProperties;
        this.dayScheduler = dayScheduler;
        this.typeLabels = typeLabels;
        this.versions = new ItineraryVersions(itineraryRepository, stopRepository, restaurantRepository, typeLabels,
                reasonContextLoader, lock, transactionManager);
    }

    // ===================== 삭제 =====================

    /** 항목 하나(관광지·점심·저녁)를 지운다. 그날에 항목이 하나뿐이면 지울 수 없다 */
    public ItineraryResponseDto deleteStop(Long userId, Long tripId, Long stopId) {
        return versions.locked(tripId, () -> doDelete(userId, tripId, stopId));
    }

    private ItineraryResponseDto doDelete(Long userId, Long tripId, Long stopId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        ItineraryVersions.LatestStop found = versions.latestStop(tripId, stopId);
        Itinerary latest = found.latest();
        ItineraryDay day = found.day();
        ItineraryStop stop = found.stop();
        if (day.getStops().size() <= 1) {
            throw new IllegalArgumentException("하루에 일정이 하나는 있어야 해요. 다른 장소로 바꾸거나 새 장소를 넣은 뒤 지워주세요.");
        }

        List<Slot> slots = day.getStops().stream().filter(s -> s != stop).map(Slot::of).toList();
        String note = "DAY" + day.getDayNumber() + " " + kindOf(stop) + " 삭제: " + nameOf(stop);
        // 지운 식당의 정기 휴일 안내는 더 이상 맞지 않으므로 함께 지운다
        Predicate<String> keepWarning = stop.getRestaurant() == null
                ? w -> true
                : w -> !w.startsWith(stop.getRestaurant().getName() + "은(는)");

        ItineraryResponseDto response = saveEdited(context(trip, latest, day), slots, note, keepWarning, null);
        log.info("[Itinerary] 항목 삭제 tripId={} 새 버전 {} DAY{} {}", tripId, response.version(),
                day.getDayNumber(), nameOf(stop));
        return response;
    }

    // ===================== 순서 변경 =====================

    /** 같은 날 안에서 항목 순서를 바꾼다. stopIds는 그날의 모든 항목 id를 원하는 순서대로 */
    public ItineraryResponseDto reorder(Long userId, Long tripId, int dayNumber, List<Long> stopIds, Integer version) {
        return versions.locked(tripId, () -> doReorder(userId, tripId, dayNumber, stopIds, version));
    }

    private ItineraryResponseDto doReorder(Long userId, Long tripId, int dayNumber, List<Long> stopIds, Integer version) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        Itinerary latest = versions.latest(tripId);
        ItineraryVersions.checkVersion(latest, version);
        ItineraryDay day = ItineraryVersions.dayOf(latest, dayNumber);

        List<ItineraryStop> current = day.getStops();
        Map<Long, ItineraryStop> byId = current.stream()
                .collect(Collectors.toMap(ItineraryStop::getId, Function.identity()));
        boolean sameSet = stopIds.size() == current.size()
                && new HashSet<>(stopIds).size() == stopIds.size()
                && byId.keySet().containsAll(stopIds);
        if (!sameSet) {
            if (stopIds.stream().anyMatch(id -> !byId.containsKey(id) && versions.isInOldVersion(tripId, latest, id))) {
                throw new ConflictException("일정이 바뀌었어요. 화면을 새로고침해주세요.");
            }
            throw new IllegalArgumentException("이 날의 항목을 빠짐없이 한 번씩 보내주세요.");
        }
        if (current.stream().map(ItineraryStop::getId).toList().equals(stopIds)) {
            throw new IllegalArgumentException("이미 이 순서예요.");
        }

        List<Slot> slots = stopIds.stream().map(byId::get).map(Slot::of).toList();
        String note = "DAY" + dayNumber + " 순서 변경";
        ItineraryResponseDto response = saveEdited(context(trip, latest, day), slots, note, w -> true, null);
        log.info("[Itinerary] 순서 변경 tripId={} 새 버전 {} DAY{} {}", tripId, response.version(), dayNumber,
                slots.stream().map(Slot::name).toList());
        return response;
    }

    // ===================== 관광지 추가 =====================

    /** position 자리에 넣을 수 있는 관광지 후보 (교체 후보와 같은 기준) */
    public AddAttractionCandidatesResponseDto addCandidates(Long userId, Long tripId, int dayNumber, int position) {
        return versions.read(() -> {
            Trip trip = TripAccess.owned(tripRepository, userId, tripId);
            Itinerary latest = versions.latest(tripId);
            ItineraryDay day = ItineraryVersions.dayOf(latest, dayNumber);
            checkPosition(day, position);
            Context ctx = context(trip, latest, day);

            List<ScoredAttraction> found = findCandidates(ctx, anchor(ctx, position)).stream()
                    .limit(CANDIDATE_LIMIT).toList();
            List<Slot> currentSlots = day.getStops().stream().map(Slot::of).toList();
            int baselineEnd = lastDepartureMinutes(retime(ctx, currentSlots));

            // 앞 장소(맨 앞이면 숙소)에서 각 후보까지의 이동 (이동 시간 표는 한 번만 만든다)
            TravelPoint previous = position > 0 ? pointOf(currentSlots.get(position - 1)) : ctx.hotel();
            List<TravelPoint> points = new ArrayList<>();
            points.add(previous);
            found.forEach(c -> points.add(TravelPoint.of(c.attraction())));
            TravelMatrix matrix = travelTimeService.buildMatrix(points,
                    ctx.options().mode(), ctx.options().walkThresholdMinutes());

            List<CandidateDto> candidates = new ArrayList<>();
            for (int i = 0; i < found.size(); i++) {
                Attraction a = found.get(i).attraction();
                int stay = stayOf(a, trip);
                Retimed retimed = retime(ctx, inserted(currentSlots, position, Slot.attraction(a, stay)));
                TravelLeg leg = matrix.get(0, i + 1);
                candidates.add(new CandidateDto(a.getId(), a.getName(), typeLabels.toVisibleLabels(a.getTypes()),
                        a.getRating(), a.getReviewCnt(), a.getAddress(), a.getWard(), a.getPlaceId(),
                        GeoUtils.lat(a.getLocation()), GeoUtils.lng(a.getLocation()), stay,
                        new ItineraryResponseDto.TravelDto(leg.mode(), leg.minutes(), leg.distanceMeters(), leg.estimated()),
                        lastDepartureMinutes(retimed) - baselineEnd, retimed.warnings().isEmpty(),
                        found.get(i).zone() == day.getZone(), a.getNameKo()));
            }
            boolean broadened = candidates.stream().anyMatch(c -> !c.sameZone());
            return new AddAttractionCandidatesResponseDto(dayNumber, position, broadened, candidates);
        });
    }

    /** position 자리에 관광지를 넣는다 (후보 목록에 있는 관광지만) */
    public ItineraryResponseDto addAttraction(Long userId, Long tripId, int dayNumber, Long attractionId,
                                              int position, Integer version) {
        return versions.locked(tripId, () -> doAdd(userId, tripId, dayNumber, attractionId, position, version));
    }

    private ItineraryResponseDto doAdd(Long userId, Long tripId, int dayNumber, Long attractionId,
                                       int position, Integer version) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        Itinerary latest = versions.latest(tripId);
        ItineraryVersions.checkVersion(latest, version);
        ItineraryDay day = ItineraryVersions.dayOf(latest, dayNumber);
        checkPosition(day, position);
        Context ctx = context(trip, latest, day);

        Attraction added = findCandidates(ctx, anchor(ctx, position)).stream()
                .map(ScoredAttraction::attraction)
                .filter(a -> Objects.equals(a.getId(), attractionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이 자리에 넣을 수 없는 관광지예요. 후보 목록에서 골라주세요."));

        List<Slot> slots = inserted(day.getStops().stream().map(Slot::of).toList(), position,
                Slot.attraction(added, stayOf(added, trip)));
        String note = "DAY" + dayNumber + " 관광지 추가: " + added.getName();
        ItineraryResponseDto response = saveEdited(ctx, slots, note, w -> true, added.getName());
        log.info("[Itinerary] 관광지 추가 tripId={} 새 버전 {} DAY{} {}번째 자리에 {}", tripId, response.version(),
                dayNumber, position + 1, added.getName());
        return response;
    }

    private static List<Slot> inserted(List<Slot> slots, int position, Slot slot) {
        List<Slot> result = new ArrayList<>(slots);
        result.add(position, slot);
        return result;
    }

    /**
     * 일정에 아직 안 쓴 관광지 (같은 지역이 먼저, 각각 점수 높은 순).
     * 같은 지역 후보가 적으면(3곳 미만) 넣을 자리 근처(앞 장소, 맨 앞이면 첫 장소) 3km 안의 다른 지역 관광지도 넣는다.
     * 기준 숫자는 관광지 교체와 같은 값을 쓴다.
     */
    private List<ScoredAttraction> findCandidates(Context ctx, TravelPoint anchor) {
        Set<Long> used = ctx.latest().getDays().stream()
                .flatMap(d -> d.getStops().stream())
                .filter(s -> s.getAttraction() != null)
                .map(s -> s.getAttraction().getId())
                .collect(Collectors.toSet());
        List<ScoredAttraction> eligible = attractionScorer
                .score(attractionRepository.findAll(), ItineraryPlanner.interestsOf(ctx.trip()))
                .stream()
                .filter(c -> !used.contains(c.attraction().getId()))
                .sorted(Comparator.comparingDouble(ScoredAttraction::score).reversed())
                .toList();

        List<ScoredAttraction> sameZone = eligible.stream()
                .filter(c -> c.zone() == ctx.day().getZone())
                .toList();
        if (sameZone.size() >= ItineraryAttractionEditService.MIN_SAME_ZONE_CANDIDATES) {
            return sameZone;
        }
        List<ScoredAttraction> result = new ArrayList<>(sameZone);
        eligible.stream()
                .filter(c -> c.zone() != ctx.day().getZone())
                .filter(c -> GeoUtils.distanceMeters(anchor.lat(), anchor.lng(), c.lat(), c.lng())
                        <= ItineraryAttractionEditService.NEARBY_METERS)
                .forEach(result::add);
        return result;
    }

    /** 다른 지역 후보를 찾을 기준점: 넣을 자리 앞 장소, 맨 앞이면 그날 첫 장소 */
    private static TravelPoint anchor(Context ctx, int position) {
        List<ItineraryStop> stops = ctx.day().getStops();
        if (position > 0) {
            return pointOf(Slot.of(stops.get(position - 1)));
        }
        return stops.isEmpty() ? ctx.hotel() : pointOf(Slot.of(stops.get(0)));
    }

    // ===================== 새 버전 만들기 =====================

    /**
     * 바뀐 날을 slots 순서대로 다시 계산하고, 나머지 날은 그대로 복사해서 새 버전으로 저장한다.
     * @param keepWarning 그날의 기존 안내 중 남길 것 (시간 안내는 항상 새로 계산한다)
     * @param addedName   새로 넣은 관광지 이름. "시간이 부족해서 뺀 곳"에 있으면 지운다
     */
    private ItineraryResponseDto saveEdited(Context ctx, List<Slot> slots, String note,
                                            Predicate<String> keepWarning, String addedName) {
        ItineraryDay day = ctx.day();
        Retimed retimed = retime(ctx, slots);

        List<String> warnings = day.getWarnings().stream()
                .filter(w -> !DayCopies.isTimeWarning(w) && keepWarning.test(w))
                .collect(Collectors.toCollection(ArrayList::new));
        warnings.addAll(retimed.warnings());
        List<DroppedPlace> dropped = day.getDropped().stream()
                .filter(d -> addedName == null || !Objects.equals(d.getName(), addedName))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<Object, Boolean> repeated = DayCopies.genreRepeatedFlags(ctx.latest(), day, slots,
                key -> key instanceof Slot slot ? slot.restaurant() : ((ItineraryStop) key).getRestaurant());

        Itinerary latest = ctx.latest();
        Itinerary next = ItineraryVersions.next(ctx.trip(), latest, ItinerarySource.EDITED, note);
        for (ItineraryDay d : latest.getDays()) {
            next.addDay(d == day ? editedDay(day, slots, retimed, warnings, dropped, repeated)
                    : DayCopies.copyDay(d, repeated));
        }

        versions.save(next);
        ctx.trip().markEdited();
        return versions.toResponse(next);
    }

    /** 바뀐 날: slots 순서대로, 다시 계산한 시각으로. 장소·순서가 달라졌으니 AI 설명은 비운다 */
    private static ItineraryDay editedDay(ItineraryDay day, List<Slot> slots, Retimed retimed, List<String> warnings,
                                          List<DroppedPlace> dropped, Map<Object, Boolean> repeated) {
        ItineraryDay copy = DayCopies.emptyCopy(day, null, dropped, warnings);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItineraryStop source = slot.source();
            copy.addStop(DayCopies.timed(ItineraryStop.builder(), retimed.stops().get(i))
                    .stopOrder(i + 1)
                    .type(slot.type())
                    .attraction(slot.attraction())
                    .restaurant(slot.restaurant())
                    .stayMinutes(slot.stayMinutes())
                    .budgetRelaxed(source != null && source.isBudgetRelaxed())
                    .genreRepeated(repeated.getOrDefault(slot, false))
                    .alternativeRestaurantIds(source == null ? new ArrayList<>() : source.getAlternativeRestaurantIds())
                    .build());
        }
        return copy;
    }

    private Retimed retime(Context ctx, List<Slot> slots) {
        List<RetimeItem> items = slots.stream()
                .map(s -> new RetimeItem(s.type(), pointOf(s), s.stayMinutes()))
                .toList();
        ItineraryDay day = ctx.day();
        return dayScheduler.retime(items, day.getWindowStart(), day.getWindowEnd(), ctx.hotel(), ctx.options());
    }

    // ===================== 불러오기·검사 =====================

    private static void checkPosition(ItineraryDay day, int position) {
        if (position < 0 || position > day.getStops().size()) {
            throw new IllegalArgumentException("넣을 자리가 올바르지 않습니다.");
        }
    }

    private Context context(Trip trip, Itinerary latest, ItineraryDay day) {
        TripPreference preference = preferenceRepository.findByTripId(trip.getId()).orElse(null);
        TravelOptions options = TravelOptions.of(trip, ItineraryPlanner.constraintsOf(preference), travelProperties);
        Point hotel = accommodationLocator.locate(trip);
        return new Context(trip, latest, day, options,
                TravelPoint.of(trip.getAccommodationName(), hotel));
    }

    // ===================== 작은 도우미 =====================

    private static String kindOf(ItineraryStop stop) {
        return switch (stop.getType()) {
            case ATTRACTION -> "관광지";
            case LUNCH -> "점심";
            case DINNER -> "저녁";
            case CAFE -> "카페";
        };
    }

    private static String nameOf(ItineraryStop stop) {
        return stop.getType() == StopType.ATTRACTION ? stop.getAttraction().getName() : stop.getRestaurant().getName();
    }

    private static int stayOf(Attraction attraction, Trip trip) {
        return StayTimes.scaled(StayTimes.baseOf(attraction.getTypes()), trip.getTravelPace().getStayMultiplier());
    }

    private static int lastDepartureMinutes(Retimed retimed) {
        LocalTime last = retimed.stops().get(retimed.stops().size() - 1).departure();
        return (int) Duration.between(LocalTime.MIDNIGHT, last).toMinutes();
    }

    private static TravelPoint pointOf(Slot slot) {
        Point p = slot.attraction() != null ? slot.attraction().getLocation() : slot.restaurant().getLocation();
        return TravelPoint.of(slot.name(), p);
    }

    // ===================== 내부 자료 =====================

    /** 고칠 날과 그 여행의 이동 방식·숙소 */
    private record Context(Trip trip, Itinerary latest, ItineraryDay day, TravelOptions options, TravelPoint hotel) {
    }

    /**
     * 새로 만들 하루의 한 자리. 기존 항목이면 source에 원래 항목(예산 초과·대안 식당을 이어받는다), 새로 넣은 관광지면 null.
     * "같은 음식" 표시를 자리마다 따로 기억하려고 IdentityHashMap의 열쇠로 쓴다 (record의 equals와 상관없이 자리 자체로 구분).
     */
    private record Slot(StopType type, Attraction attraction, Restaurant restaurant, int stayMinutes,
                        ItineraryStop source) {

        static Slot of(ItineraryStop stop) {
            return new Slot(stop.getType(), stop.getAttraction(), stop.getRestaurant(), stop.getStayMinutes(), stop);
        }

        static Slot attraction(Attraction attraction, int stayMinutes) {
            return new Slot(StopType.ATTRACTION, attraction, null, stayMinutes, null);
        }

        String name() {
            return attraction != null ? attraction.getName() : restaurant.getName();
        }
    }
}
