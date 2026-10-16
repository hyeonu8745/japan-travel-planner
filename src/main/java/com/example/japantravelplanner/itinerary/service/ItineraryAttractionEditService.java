package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.NightSpots;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.candidate.Shelter;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto.CandidateDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.RainPlanDto;
import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DayScheduler;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimeItem;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.Retimed;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimedStop;
import com.example.japantravelplanner.itinerary.planner.StayTimes;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.planner.TravelOptions;
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
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.example.japantravelplanner.itinerary.reason.ReasonContextLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.japantravelplanner.global.util.GeoUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 만들어진 일정의 관광지(낮 관광지 또는 저녁 뒤 야경 장소)를 다른 관광지로 바꾼다.
 *
 * 식당 교체(ItineraryEditService)와 같은 방식이다. 일정 전체를 복사해서 새 버전(EDITED)으로 저장하고,
 * 바뀐 날은 순서를 그대로 두고 시각만 다시 계산한다. 시간이 넘쳐도 항목을 빼지 않고 안내 문장으로만 알린다.
 * AI를 부르지 않으므로 하루 생성 횟수 제한(GENERATED만 센다)에 들어가지 않는다.
 *
 * 관광지는 식당과 달리 장소마다 머무는 시간이 다르므로, 새 장소의 종류로 체류 시간을 다시 계산한다.
 * (식당 교체 서비스와 겹치는 작은 도우미가 몇 개 있다. 나중에 한 곳으로 정리할 수 있다)
 */
@Slf4j
@Service
public class ItineraryAttractionEditService {

    /** 후보로 보여주는 관광지 수 */
    static final int CANDIDATE_LIMIT = 10;

    /** 같은 지역의 후보가 이 수보다 적으면 가까운 다른 지역 관광지도 후보에 넣는다 */
    static final int MIN_SAME_ZONE_CANDIDATES = 3;

    /** 바꾸려는 장소에서 이 거리(미터) 안이면 다른 지역이라도 후보가 된다 (지역을 나눌 때 쓰는 기준과 같다) */
    static final double NEARBY_METERS = 3000;

    /** Itinerary의 (여행, 버전 번호) 유일 제약 이름 */
    private static final String VERSION_UNIQUE_CONSTRAINT = "uk_itineraries_trip_version";

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
    private final GenerationLock lock;
    private final ReasonContextLoader reasonContextLoader;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryAttractionEditService(TripRepository tripRepository,
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
        this.lock = lock;
        this.reasonContextLoader = reasonContextLoader;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 후보 조회 =====================

    public AttractionCandidatesResponseDto candidates(Long userId, Long tripId, Long stopId) {
        return readTransaction.execute(status -> {
            Target target = load(userId, tripId, stopId);
            List<ScoredAttraction> found = findCandidates(target).stream().limit(CANDIDATE_LIMIT).toList();
            return toCandidatesResponse(target, found);
        });
    }

    private AttractionCandidatesResponseDto toCandidatesResponse(Target target, List<ScoredAttraction> found) {
        // 앞 장소에서 각 후보까지의 이동 (이동 시간 표는 한 번만 만든다)
        List<TravelPoint> points = new ArrayList<>();
        points.add(target.previous());
        found.forEach(c -> points.add(pointOf(c.attraction())));
        TravelMatrix matrix = travelTimeService.buildMatrix(points,
                target.options().mode(), target.options().walkThresholdMinutes());

        // 지금 일정이 끝나는 시각을 기준으로, 후보로 바꾸면 얼마나 달라지는지 비교한다
        int baselineEnd = lastDepartureMinutes(retimeDay(target, null, 0));

        List<CandidateDto> candidates = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            Attraction a = found.get(i).attraction();
            int stay = stayOf(a, target);
            Retimed retimed = retimeDay(target, a, stay);
            TravelLeg leg = matrix.get(0, i + 1);
            candidates.add(new CandidateDto(a.getId(), a.getName(), typeLabels.toVisibleLabels(a.getTypes()),
                    a.getRating(), a.getReviewCnt(), a.getAddress(), a.getWard(), a.getPlaceId(),
                    lat(a.getLocation()), lng(a.getLocation()), stay,
                    new ItineraryResponseDto.TravelDto(leg.mode(), leg.minutes(), leg.distanceMeters(), leg.estimated()),
                    lastDepartureMinutes(retimed) - baselineEnd, retimed.warnings().isEmpty(),
                    found.get(i).zone() == target.day().getZone(), a.getNameKo()));
        }
        boolean broadened = candidates.stream().anyMatch(c -> !c.sameZone());
        return new AttractionCandidatesResponseDto(target.stop().getId(), target.night(), broadened, candidates);
    }

    // ===================== 교체 =====================

    public ItineraryResponseDto replace(Long userId, Long tripId, Long stopId, Long attractionId) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("이미 일정을 만들거나 고치고 있어요. 잠시만 기다려주세요.");
        }
        try {
            return writeTransaction.execute(status -> doReplace(userId, tripId, stopId, attractionId));
        } finally {
            lock.release(tripId);
        }
    }

    private ItineraryResponseDto doReplace(Long userId, Long tripId, Long stopId, Long attractionId) {
        Target target = load(userId, tripId, stopId);
        ItineraryStop stop = target.stop();

        if (Objects.equals(stop.getAttraction().getId(), attractionId)) {
            throw new IllegalArgumentException("이미 이 관광지로 정해져 있어요.");
        }
        Attraction replacement = findCandidates(target).stream()
                .map(ScoredAttraction::attraction)
                .filter(a -> Objects.equals(a.getId(), attractionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이 자리에 넣을 수 없는 관광지예요. 후보 목록에서 골라주세요."));
        int stay = stayOf(replacement, target);

        Itinerary latest = target.latest();
        Itinerary next = Itinerary.create(target.trip(), latest.getVersionNo() + 1, latest.getKrwPerYen(),
                ItinerarySource.EDITED, replaceNote(target, replacement)).copyExchangeRateFrom(latest);
        for (ItineraryDay day : latest.getDays()) {
            boolean edited = day.getDayNumber() == target.day().getDayNumber();
            next.addDay(edited ? retimedDay(target, replacement, stay) : copyDay(day));
        }

        saveNewVersion(next);
        target.trip().markEdited();

        log.info("[Itinerary] 관광지 교체 tripId={} version={} DAY{} {} → {}", tripId, next.getVersionNo(),
                target.day().getDayNumber(), stop.getAttraction().getName(), replacement.getName());
        return toResponse(next);
    }

    /** 변경 이력에 보여줄 한 줄: "DAY2 관광지: なだれ坂 → 旧芝離宮" (저녁 뒤 장소면 "야경") */
    private static String replaceNote(Target target, Attraction replacement) {
        String kind = target.night() ? "야경" : "관광지";
        return "DAY" + target.day().getDayNumber() + " " + kind + ": " + target.stop().getAttraction().getName()
                + " → " + replacement.getName();
    }

    // ===================== 새 버전 만들기 =====================

    /** 관광지가 바뀐 날: 순서는 그대로, 시각만 다시 계산하고 설명은 비운다 */
    private ItineraryDay retimedDay(Target target, Attraction replacement, int stay) {
        return retimedDay(target, swapOf(target.stop(), replacement, stay));
    }

    /** 10-16: 여러 곳을 한 번에 바꾼 날 (비 오는 날 대안). 바꾸지 않는 항목은 그대로 */
    private ItineraryDay retimedDay(Target target, Map<ItineraryStop, Swap> swaps) {
        ItineraryDay day = target.day();
        Retimed retimed = retimeDay(target, swaps);
        Set<String> replacedNames = swaps.values().stream().map(sw -> sw.attraction().getName()).collect(Collectors.toSet());

        // 시간 안내는 새로 계산한 것으로 바꾸고, 나머지 안내(휴일 등)는 그대로 둔다
        List<String> warnings = day.getWarnings().stream()
                .filter(w -> !isTimeWarning(w))
                .collect(Collectors.toCollection(ArrayList::new));
        warnings.addAll(retimed.warnings());

        // 시간이 부족해서 뺐던 곳을 사용자가 직접 골랐다면, 이제 일정에 들어 있으므로 "뺀 곳"에서 지운다
        List<DroppedPlace> dropped = day.getDropped().stream()
                .filter(d -> !replacedNames.contains(d.getName()))
                .collect(Collectors.toCollection(ArrayList::new));

        ItineraryDay copy = ItineraryDay.create(day.getDayNumber(), day.getDate(), day.getZone(),
                day.getWindowStart(), day.getWindowEnd(), null, dropped, warnings);

        List<ItineraryStop> stops = day.getStops();
        for (int i = 0; i < stops.size(); i++) {
            ItineraryStop s = stops.get(i);
            RetimedStop timed = retimed.stops().get(i);
            Swap swap = swaps.get(s);
            copy.addStop(ItineraryStop.builder()
                    .stopOrder(s.getStopOrder())
                    .type(s.getType())
                    .attraction(swap != null ? swap.attraction() : s.getAttraction())
                    .restaurant(s.getRestaurant())
                    .arrivalTime(timed.arrival())
                    .departureTime(timed.departure())
                    .stayMinutes(swap != null ? swap.stay() : s.getStayMinutes())
                    .waitMinutes(timed.waitMinutes())
                    .travelMode(timed.travelIn().mode())
                    .travelMinutes(timed.travelIn().minutes())
                    .travelDistanceMeters(timed.travelIn().distanceMeters())
                    .travelEstimated(timed.travelIn().estimated())
                    .budgetRelaxed(s.isBudgetRelaxed())
                    .genreRepeated(s.isGenreRepeated())
                    .alternativeRestaurantIds(s.getAlternativeRestaurantIds())
                    .build());
        }
        return copy;
    }

    /** 바뀌지 않은 날: 그대로 복사 */
    private ItineraryDay copyDay(ItineraryDay day) {
        ItineraryDay copy = ItineraryDay.create(day.getDayNumber(), day.getDate(), day.getZone(),
                day.getWindowStart(), day.getWindowEnd(), day.getDescription(),
                new ArrayList<>(day.getDropped()), new ArrayList<>(day.getWarnings()));
        for (ItineraryStop s : day.getStops()) {
            copy.addStop(ItineraryStop.builder()
                    .stopOrder(s.getStopOrder())
                    .type(s.getType())
                    .attraction(s.getAttraction())
                    .restaurant(s.getRestaurant())
                    .arrivalTime(s.getArrivalTime())
                    .departureTime(s.getDepartureTime())
                    .stayMinutes(s.getStayMinutes())
                    .waitMinutes(s.getWaitMinutes())
                    .travelMode(s.getTravelMode())
                    .travelMinutes(s.getTravelMinutes())
                    .travelDistanceMeters(s.getTravelDistanceMeters())
                    .travelEstimated(s.isTravelEstimated())
                    .budgetRelaxed(s.isBudgetRelaxed())
                    .genreRepeated(s.isGenreRepeated())
                    .alternativeRestaurantIds(s.getAlternativeRestaurantIds())
                    .build());
        }
        return copy;
    }

    private static boolean isTimeWarning(String warning) {
        return warning.startsWith("점심 시간이") || warning.startsWith("저녁 시간이") || warning.startsWith("일정이 ");
    }

    /**
     * 그날 순서 그대로 시각을 다시 계산한다.
     * @param replacement 대상 항목 자리에 넣어볼 관광지. null이면 지금 그대로 (비교 기준용)
     */
    private Retimed retimeDay(Target target, Attraction replacement, int replacementStay) {
        return retimeDay(target, replacement == null ? Map.of() : swapOf(target.stop(), replacement, replacementStay));
    }

    private Retimed retimeDay(Target target, Map<ItineraryStop, Swap> swaps) {
        ItineraryDay day = target.day();
        List<RetimeItem> items = new ArrayList<>();
        for (ItineraryStop s : day.getStops()) {
            Swap swap = swaps.get(s);
            if (swap != null) {
                items.add(new RetimeItem(StopType.ATTRACTION, pointOf(swap.attraction()), swap.stay()));
            } else {
                items.add(new RetimeItem(s.getType(), pointOf(s), s.getStayMinutes()));
            }
        }
        return dayScheduler.retime(items, day.getWindowStart(), day.getWindowEnd(), target.hotel(), target.options());
    }

    private static int lastDepartureMinutes(Retimed retimed) {
        var last = retimed.stops().get(retimed.stops().size() - 1).departure();
        return (int) Duration.between(java.time.LocalTime.MIDNIGHT, last).toMinutes();
    }

    private void saveNewVersion(Itinerary next) {
        try {
            itineraryRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException e) {
            Throwable cause = e.getMostSpecificCause();
            String message = cause.getMessage() == null ? "" : cause.getMessage();
            if (message.contains(VERSION_UNIQUE_CONSTRAINT)) {
                throw new ConflictException("일정이 동시에 바뀌었어요. 화면을 새로고침해주세요.");
            }
            log.error("[Itinerary] 새 버전 저장이 DB에서 거절됨 (동시성 문제가 아님): {}", message);
            throw e;
        }
    }

    // ===================== 비 오는 날 대안 (10-16) =====================

    /**
     * 그날 실외 관광지(Shelter.OUTDOOR)를 같은 지역(모자라면 3km 안)의 실내 관광지로 바꾼 일정 미리보기.
     * 후보는 교체 후보와 같은 규칙(findCandidates: 점수순, 일정에 없는 곳, 야경 자리는 야경 장소)에서 실내인 곳 중 첫째.
     * 식당과 순서는 그대로, 시각만 다시 계산한다. 저장하지 않는다
     */
    public RainPlanDto rainPlan(Long userId, Long tripId, int dayNumber) {
        return readTransaction.execute(status -> toRainPlanDto(planRain(userId, tripId, dayNumber)));
    }

    /** 미리보기와 같은 계산으로 새 버전(EDITED) 하나를 만든다. version이 지금 일정과 다르면 409 */
    public ItineraryResponseDto applyRainPlan(Long userId, Long tripId, int dayNumber, Integer version) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("이미 일정을 만들거나 고치고 있어요. 잠시만 기다려주세요.");
        }
        try {
            return writeTransaction.execute(status -> {
                RainPlan plan = planRain(userId, tripId, dayNumber);
                if (version != null && version != plan.latest().getVersionNo()) {
                    throw new ConflictException("일정이 바뀌었어요. 화면을 새로고침해주세요.");
                }
                if (plan.swaps().isEmpty()) {
                    throw new IllegalArgumentException("이 날은 실내로 바꿀 관광지가 없어요.");
                }
                Itinerary latest = plan.latest();
                Itinerary next = Itinerary.create(plan.trip(), latest.getVersionNo() + 1, latest.getKrwPerYen(),
                        ItinerarySource.EDITED, rainNote(dayNumber, plan.swaps())).copyExchangeRateFrom(latest);
                for (ItineraryDay day : latest.getDays()) {
                    next.addDay(day.getDayNumber() == dayNumber ? retimedDay(plan.base(), plan.swaps()) : copyDay(day));
                }
                saveNewVersion(next);
                plan.trip().markEdited();
                log.info("[Itinerary] 비 오는 날 대안 적용 tripId={} version={} DAY{} {}곳", tripId, next.getVersionNo(),
                        dayNumber, plan.swaps().size());
                return toResponse(next);
            });
        } finally {
            lock.release(tripId);
        }
    }

    private RainPlan planRain(Long userId, Long tripId, int dayNumber) {
        Trip trip = findTrip(userId, tripId);
        Itinerary latest = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .orElseThrow(() -> new NotFoundException("아직 만들어진 일정이 없습니다."));
        ItineraryDay day = latest.getDays().stream().filter(d -> d.getDayNumber() == dayNumber).findFirst()
                .orElseThrow(() -> new NotFoundException(dayNumber + "일차 일정이 없어요."));

        Map<ItineraryStop, Swap> swaps = new java.util.LinkedHashMap<>();
        List<String> kept = new ArrayList<>();
        Set<Long> chosen = new java.util.HashSet<>();
        Target base = null;
        int outdoor = 0;
        for (ItineraryStop stop : day.getStops()) {
            if (stop.getType() != StopType.ATTRACTION || Shelter.of(stop.getAttraction()) != Shelter.OUTDOOR) continue;
            outdoor++;
            Target target = targetFor(trip, latest, day, stop);
            if (base == null) base = target;
            Attraction indoor = findCandidates(target).stream()
                    .map(ScoredAttraction::attraction)
                    .filter(a -> Shelter.of(a) == Shelter.INDOOR && !chosen.contains(a.getId()))
                    .findFirst().orElse(null);
            if (indoor == null) {
                kept.add(stop.getAttraction().getName());
                continue;
            }
            chosen.add(indoor.getId());
            swaps.put(stop, new Swap(indoor, stayOf(indoor, target)));
        }
        Retimed retimed = swaps.isEmpty() ? null : retimeDay(base, swaps);
        return new RainPlan(trip, latest, day, base, swaps, kept, outdoor, retimed);
    }

    private RainPlanDto toRainPlanDto(RainPlan plan) {
        List<RainPlanDto.SwapDto> swaps = plan.swaps().entrySet().stream()
                .map(e -> new RainPlanDto.SwapDto(e.getKey().getId(), e.getKey().getStopOrder(),
                        place(e.getKey().getAttraction(), e.getKey().getStayMinutes()),
                        place(e.getValue().attraction(), e.getValue().stay())))
                .toList();
        List<RainPlanDto.PreviewStopDto> stops = new ArrayList<>();
        List<ItineraryStop> dayStops = plan.day().getStops();
        for (int i = 0; i < dayStops.size(); i++) {
            ItineraryStop s = dayStops.get(i);
            Swap swap = plan.swaps().get(s);
            String name = swap != null ? swap.attraction().getName()
                    : s.getAttraction() != null ? s.getAttraction().getName() : s.getRestaurant().getName();
            String nameKo = swap != null ? swap.attraction().getNameKo()
                    : s.getAttraction() != null ? s.getAttraction().getNameKo() : s.getRestaurant().getNameKo();
            RetimedStop timed = plan.retimed() == null ? null : plan.retimed().stops().get(i);
            stops.add(new RainPlanDto.PreviewStopDto(s.getStopOrder(), s.getType(), name, nameKo,
                    timed == null ? s.getArrivalTime() : timed.arrival(),
                    timed == null ? s.getDepartureTime() : timed.departure(), swap != null));
        }
        return new RainPlanDto(plan.trip().getId(), plan.latest().getVersionNo(), plan.day().getDayNumber(),
                !swaps.isEmpty(), rainMessage(plan.outdoor(), swaps.size(), plan.kept()), swaps, List.copyOf(plan.kept()),
                plan.retimed() == null ? List.of() : List.copyOf(plan.retimed().warnings()), stops);
    }

    static String rainMessage(int outdoor, int swapped, List<String> kept) {
        if (outdoor == 0) return "이 날은 실외 관광지가 없어 비가 와도 그대로 다니면 돼요.";
        if (swapped == 0) return "근처에 바꿀 만한 실내 장소가 없어요. 우산을 챙겨 그대로 다니거나 다른 날과 바꿔 보세요.";
        String result = "실외 관광지 " + swapped + "곳을 근처 실내 장소로 바꿨어요.";
        if (!kept.isEmpty()) result += " " + String.join(", ", kept) + "은(는) 바꿀 곳이 없어 그대로 두었어요.";
        return result;
    }

    /** 변경 이력: "DAY2 비 오는 날 일정: 新宿御苑 → 東京都庁展望室, 明治神宮 → ルミネ新宿" */
    private static String rainNote(int dayNumber, Map<ItineraryStop, Swap> swaps) {
        return "DAY" + dayNumber + " 비 오는 날 일정: " + swaps.entrySet().stream()
                .map(e -> e.getKey().getAttraction().getName() + " → " + e.getValue().attraction().getName())
                .collect(Collectors.joining(", "));
    }

    private RainPlanDto.PlaceDto place(Attraction a, int stay) {
        return new RainPlanDto.PlaceDto(a.getId(), a.getName(), a.getNameKo(), typeLabels.toVisibleLabels(a.getTypes()),
                lat(a.getLocation()), lng(a.getLocation()), stay);
    }

    private static Map<ItineraryStop, Swap> swapOf(ItineraryStop stop, Attraction attraction, int stay) {
        Map<ItineraryStop, Swap> map = new java.util.IdentityHashMap<>();
        map.put(stop, new Swap(attraction, stay));
        return map;
    }

    // ===================== 후보 찾기 =====================

    /**
     * 일정에 아직 안 쓴 관광지 (같은 지역이 먼저, 각각 점수 높은 순).
     * 점수·리뷰 100개 이상·같은 장소(150m) 합치기는 일정을 만들 때와 같은 AttractionScorer가 처리한다.
     * 저녁 뒤 야경 장소를 바꾸는 경우에는 야경 장소(전망대 + 대표 야경 명소, NightSpots)만 후보가 된다.
     *
     * 같은 지역에 남은 후보가 적으면(3곳 미만) 바꾸려는 장소에서 3km 안의 다른 지역 관광지도 넣는다.
     * 지역마다 리뷰 기준을 통과하는 관광지가 몇 곳뿐이라, 일정이 그 지역 관광지를 거의 다 쓰고 있으면
     * 바꿀 곳이 하나도 없는 막다른 길이 되기 때문이다.
     */
    private List<ScoredAttraction> findCandidates(Target target) {
        Set<Long> used = usedAttractionIds(target.latest());
        List<ScoredAttraction> eligible = attractionScorer
                .score(attractionRepository.findAll(), ItineraryPlanner.interestsOf(target.trip()))
                .stream()
                .filter(c -> !used.contains(c.attraction().getId()))
                .filter(c -> !target.night() || NightSpots.isNightCandidate(c.attraction()))
                .sorted(Comparator.comparingDouble(ScoredAttraction::score).reversed())
                .toList();

        List<ScoredAttraction> sameZone = eligible.stream()
                .filter(c -> c.zone() == target.day().getZone())
                .toList();
        if (sameZone.size() >= MIN_SAME_ZONE_CANDIDATES) {
            return sameZone;
        }

        Point here = target.stop().getAttraction().getLocation();
        List<ScoredAttraction> nearby = eligible.stream()
                .filter(c -> c.zone() != target.day().getZone())
                .filter(c -> GeoUtils.distanceMeters(here.getY(), here.getX(), c.lat(), c.lng()) <= NEARBY_METERS)
                .toList();

        List<ScoredAttraction> result = new ArrayList<>(sameZone);
        result.addAll(nearby);
        return result;
    }

    private static Set<Long> usedAttractionIds(Itinerary itinerary) {
        return itinerary.getDays().stream()
                .flatMap(day -> day.getStops().stream())
                .filter(s -> s.getAttraction() != null)
                .map(s -> s.getAttraction().getId())
                .collect(Collectors.toSet());
    }

    // ===================== 불러오기·검사 =====================

    private Target load(Long userId, Long tripId, Long stopId) {
        Trip trip = findTrip(userId, tripId);
        ItineraryStop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new NotFoundException("일정 항목을 찾을 수 없습니다."));
        ItineraryDay day = stop.getDay();
        Itinerary itinerary = day.getItinerary();
        if (!Objects.equals(tripId, itinerary.getTrip().getId())) {
            throw new NotFoundException("일정 항목을 찾을 수 없습니다.");   // 다른 여행의 항목인지는 알려주지 않는다
        }

        Itinerary latest = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .orElseThrow(() -> new NotFoundException("아직 만들어진 일정이 없습니다."));
        if (!Objects.equals(latest.getId(), itinerary.getId())) {
            throw new ConflictException("일정이 바뀌었어요. 화면을 새로고침해주세요.");
        }
        if (stop.getType() != StopType.ATTRACTION) {
            throw new IllegalArgumentException("관광지 항목만 바꿀 수 있어요. 식당은 식당 교체를 이용해주세요.");
        }
        return targetFor(trip, latest, day, stop);
    }

    private Target targetFor(Trip trip, Itinerary latest, ItineraryDay day, ItineraryStop stop) {
        Long tripId = trip.getId();
        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);
        TravelOptions options = TravelOptions.of(trip, ItineraryPlanner.constraintsOf(preference), travelProperties);
        Point hotelLocation = accommodationLocator.locate(trip);
        TravelPoint hotel = new TravelPoint(trip.getAccommodationName(), hotelLocation.getY(), hotelLocation.getX());

        List<ItineraryStop> stops = day.getStops();
        int index = stops.indexOf(stop);
        int dinnerIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getType() == StopType.DINNER) dinnerIndex = i;
        }
        boolean night = dinnerIndex >= 0 && index > dinnerIndex;   // 저녁 뒤의 관광지는 야경 장소
        TravelPoint previous = index > 0 ? pointOf(stops.get(index - 1)) : hotel;

        return new Target(trip, latest, day, stop, night, options, hotel, previous);
    }

    private Trip findTrip(Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
    }

    // 식당 대안 후보는 id만 저장되어 있어서, 일정 전체의 대안을 한 번에 조회해서 풀어준다
    private ItineraryResponseDto toResponse(Itinerary itinerary) {
        Set<Long> ids = ItineraryResponseDto.alternativeRestaurantIds(itinerary);
        Map<Long, Restaurant> alternatives = ids.isEmpty() ? Map.of()
                : restaurantRepository.findAllById(ids).stream()
                  .collect(Collectors.toMap(Restaurant::getId, Function.identity()));
        return ItineraryResponseDto.from(itinerary, typeLabels::toVisibleLabels, alternatives,
                reasonContextLoader.load(itinerary.getTrip()));
    }

    // ===================== 좌표·계산 =====================

    // 야경 자리는 일정을 만들 때와 같은 야경 체류 시간 (전망대가 아닌 곳은 짧게)
    private static int stayOf(Attraction attraction, Target target) {
        int base = target.night() ? StayTimes.nightBaseOf(attraction.getTypes()) : StayTimes.baseOf(attraction.getTypes());
        return StayTimes.scaled(base, target.trip().getTravelPace().getStayMultiplier());
    }

    private static TravelPoint pointOf(ItineraryStop stop) {
        return stop.getType() == StopType.ATTRACTION
                ? pointOf(stop.getAttraction())
                : new TravelPoint(stop.getRestaurant().getName(),
                stop.getRestaurant().getLocation().getY(), stop.getRestaurant().getLocation().getX());
    }

    private static TravelPoint pointOf(Attraction a) {
        return new TravelPoint(a.getName(), a.getLocation().getY(), a.getLocation().getX());
    }

    // JTS 좌표는 (경도 x, 위도 y) 순서
    private static Double lat(Point point) {
        return point == null ? null : point.getY();
    }

    private static Double lng(Point point) {
        return point == null ? null : point.getX();
    }

    // ===================== 내부 자료 =====================

    /**
     * 바꿀 관광지와 그 주변.
     * @param night    저녁 뒤 야경 장소인지
     * @param previous 앞 장소 (첫 항목이면 숙소)
     */
    private record Target(Trip trip, Itinerary latest, ItineraryDay day, ItineraryStop stop, boolean night,
                          TravelOptions options, TravelPoint hotel, TravelPoint previous) {
    }

    /** 한 항목을 바꿀 관광지와 체류 시간 */
    private record Swap(Attraction attraction, int stay) {
    }

    /** @param outdoor 그날 실외 관광지 수, base 시각 계산 기준(첫 실외 항목) */
    private record RainPlan(Trip trip, Itinerary latest, ItineraryDay day, Target base, Map<ItineraryStop, Swap> swaps,
                            List<String> kept, int outdoor, Retimed retimed) {
    }
}