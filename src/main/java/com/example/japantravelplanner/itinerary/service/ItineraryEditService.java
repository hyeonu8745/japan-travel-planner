package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.policy.SensitivePlaces;

import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.StopCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.StopCandidatesResponseDto.CandidateDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.itinerary.meal.FoodFilterResolver;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.MealBudget;
import com.example.japantravelplanner.itinerary.meal.MealBudgetCalculator;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;
import com.example.japantravelplanner.itinerary.planner.DayScheduler;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimeItem;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.Retimed;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimedStop;
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
import com.example.japantravelplanner.restaurant.match.RestaurantMatchService;
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
import com.example.japantravelplanner.itinerary.meal.RegularHoliday;
import com.example.japantravelplanner.itinerary.naming.GenreLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryVersionDto;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 만들어진 일정의 식당(점심·저녁)을 다른 식당으로 바꾼다.
 *
 * 바꾸면 일정 전체를 복사해서 **새 버전(EDITED)**으로 저장하고 이전 버전은 그대로 둔다 (변경 이력·되돌리기의 바탕).
 * AI를 부르지 않으므로 하루 생성 횟수 제한(GENERATED만 센다)에 들어가지 않는다.
 *
 * 바뀐 날은 순서는 그대로 두고 시각만 다시 계산한다 (사용자가 고른 일정을 서버가 마음대로 줄이지 않는다).
 * 시간이 안 맞게 되면 안내 문장으로만 알린다. 바뀐 날의 AI 설명은 식당 이름이 달라져서 비워둔다.
 */
@Slf4j
@Service
public class ItineraryEditService {

    /** 후보로 보여주는 식당 수 */
    static final int CANDIDATE_LIMIT = 10;
    /** 고른 식당이 후보에 있는지 확인할 때 넓게 찾는 수 */
    static final int VERIFY_LIMIT = 50;
    /** 한 항목에 남겨두는 대안 수 */
    static final int MAX_ALTERNATIVES = 5;

    private static final double DEFAULT_FOOD_RATIO = 0.4;

    static final String CLOSED_RESTAURANT_MESSAGE = "이 식당은 폐업했을 수 있어요. 다른 후보를 골라주세요.";

    /** Itinerary의 (여행, 버전 번호) 유일 제약 이름 */
    private static final String VERSION_UNIQUE_CONSTRAINT = "uk_itineraries_trip_version";

    private final TripRepository tripRepository;
    private final TripPreferenceRepository preferenceRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryStopRepository stopRepository;
    private final RestaurantRepository restaurantRepository;
    private final AccommodationLocator accommodationLocator;
    private final RestaurantFinder restaurantFinder;
    private final MealBudgetCalculator mealBudgetCalculator;
    private final FoodFilterResolver foodFilterResolver;
    private final TravelTimeService travelTimeService;
    private final TravelProperties travelProperties;
    private final DayScheduler dayScheduler;
    private final PlaceTypeLabels typeLabels;
    private final GenerationLock lock;
    private final RestaurantMatchService restaurantMatchService;
    private final ReasonContextLoader reasonContextLoader;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryEditService(TripRepository tripRepository,
                                TripPreferenceRepository preferenceRepository,
                                ItineraryRepository itineraryRepository,
                                ItineraryStopRepository stopRepository,
                                RestaurantRepository restaurantRepository,
                                AccommodationLocator accommodationLocator,
                                RestaurantFinder restaurantFinder,
                                MealBudgetCalculator mealBudgetCalculator,
                                FoodFilterResolver foodFilterResolver,
                                TravelTimeService travelTimeService,
                                TravelProperties travelProperties,
                                DayScheduler dayScheduler,
                                PlaceTypeLabels typeLabels,
                                GenerationLock lock,
                                RestaurantMatchService restaurantMatchService,
                                ReasonContextLoader reasonContextLoader,
                                PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.preferenceRepository = preferenceRepository;
        this.itineraryRepository = itineraryRepository;
        this.stopRepository = stopRepository;
        this.restaurantRepository = restaurantRepository;
        this.accommodationLocator = accommodationLocator;
        this.restaurantFinder = restaurantFinder;
        this.mealBudgetCalculator = mealBudgetCalculator;
        this.foodFilterResolver = foodFilterResolver;
        this.travelTimeService = travelTimeService;
        this.travelProperties = travelProperties;
        this.dayScheduler = dayScheduler;
        this.typeLabels = typeLabels;
        this.lock = lock;
        this.restaurantMatchService = restaurantMatchService;
        this.reasonContextLoader = reasonContextLoader;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 변경 이력 =====================

    /** 이 여행의 모든 버전 (최신이 먼저). 일정을 아직 안 만들었으면 빈 목록 */
    public List<ItineraryVersionDto> versions(Long userId, Long tripId) {
        return readTransaction.execute(status -> {
            findTrip(userId, tripId);
            List<Itinerary> all = itineraryRepository.findByTripIdOrderByVersionNoDesc(tripId);
            int latestNo = all.isEmpty() ? 0 : all.get(0).getVersionNo();
            return all.stream()
                    .map(i -> new ItineraryVersionDto(i.getVersionNo(), i.getSource(), i.getCreatedAt(),
                            i.getChangeNote(), i.getVersionNo() == latestNo))
                    .toList();
        });
    }

    /** 특정 버전의 일정 (보기만 가능. 옛 버전의 항목은 고칠 수 없다) */
    public ItineraryResponseDto version(Long userId, Long tripId, int versionNo) {
        return readTransaction.execute(status -> {
            findTrip(userId, tripId);
            Itinerary itinerary = itineraryRepository.findByTripIdAndVersionNo(tripId, versionNo)
                    .orElseThrow(() -> new NotFoundException("해당 버전의 일정을 찾을 수 없습니다."));
            return toResponse(itinerary);
        });
    }

    /**
     * 옛 버전을 그대로 복사해서 새 버전(RESTORED)으로 만든다. 옛 버전과 중간 버전은 그대로 남는다.
     * 옛 버전에 저장된 AI 설명이 그 버전의 식당과 맞으므로 설명도 그대로 돌아온다.
     */
    public ItineraryResponseDto restore(Long userId, Long tripId, int versionNo) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("이미 일정을 만들거나 고치고 있어요. 잠시만 기다려주세요.");
        }
        try {
            return writeTransaction.execute(status -> doRestore(userId, tripId, versionNo));
        } finally {
            lock.release(tripId);
        }
    }

    /** 야스쿠니 등 민감 장소(SensitivePlaces)가 들어 있는 버전인지 */
    static boolean hasSensitivePlace(Itinerary itinerary) {
        return itinerary.getDays().stream().flatMap(d -> d.getStops().stream())
                .anyMatch(s -> s.getAttraction() != null && SensitivePlaces.isExcluded(s.getAttraction().getName()));
    }

    private ItineraryResponseDto doRestore(Long userId, Long tripId, int versionNo) {
        Trip trip = findTrip(userId, tripId);
        Itinerary latest = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .orElseThrow(() -> new NotFoundException("아직 만들어진 일정이 없습니다."));
        Itinerary old = itineraryRepository.findByTripIdAndVersionNo(tripId, versionNo)
                .orElseThrow(() -> new NotFoundException("해당 버전의 일정을 찾을 수 없습니다."));
        if (old.getVersionNo() == latest.getVersionNo()) {
            throw new IllegalArgumentException("이미 현재 버전이에요.");
        }
        if (hasSensitivePlace(old)) {   // 민감 장소 필터(10-13) 전에 만든 버전이면 되돌리지 않는다
            throw new ConflictException("이 버전에는 지금은 추천하지 않는 장소가 있어 되돌릴 수 없어요. 일정을 다시 만들어 주세요.");
        }

        Itinerary next = Itinerary.create(trip, latest.getVersionNo() + 1, old.getKrwPerYen(),
                ItinerarySource.RESTORED, "버전 " + old.getVersionNo() + "(으)로 되돌렸어요").copyExchangeRateFrom(old);
        for (ItineraryDay day : old.getDays()) {
            next.addDay(copyDay(day, Map.of()));   // 같은 음식 표시도 그 버전의 값 그대로
        }

        saveNewVersion(next);
        trip.markEdited();   // 확인 대기(완성 전)면 그대로

        log.info("[Itinerary] 버전 복원 tripId={} 버전 {} → 새 버전 {}", tripId, old.getVersionNo(), next.getVersionNo());
        return toResponse(next);
    }

    /**
     * 새 버전을 저장한다. 같은 버전 번호로 동시에 저장하려 한 경우(유일 제약)만 409로 안내한다
     * (잠금은 서버 한 대 안에서만 막아준다). 그 밖의 DB 거절(허용 값 제약 등)은 동시성 문제가 아니므로
     * 원인을 로그에 남기고 그대로 던진다. 전부 409로 바꾸면 진짜 원인이 가려진다.
     */
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

    private Trip findTrip(Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
    }

    // ===================== 후보 조회 =====================

    public StopCandidatesResponseDto candidates(Long userId, Long tripId, Long stopId) {
        return candidates(userId, tripId, stopId, Set.of());
    }

    /**
     * @param wantedGenres 원하는 장르 (채팅 수정 "스시로 바꿔줘"). 있으면 그 장르만 더 넓게·예산을 올려서 찾는다
     *                     (RestaurantFinder.findWanted). 비어 있으면 보통 후보
     */
    public StopCandidatesResponseDto candidates(Long userId, Long tripId, Long stopId, Set<String> wantedGenres) {
        return readTransaction.execute(status -> {
            Target target = load(userId, tripId, stopId);
            Search search = search(target, CANDIDATE_LIMIT, wantedGenres);
            return toCandidatesResponse(target, search);
        });
    }

    private StopCandidatesResponseDto toCandidatesResponse(Target target, Search search) {
        Set<String> previousMeal = previousMealGenres(target.latest(), target.stop());
        LocalDate visitDate = target.day().getDate();

        // 앞 장소에서 각 후보까지의 이동 (이동 시간 표는 한 번만 만든다)
        List<TravelPoint> points = new ArrayList<>();
        points.add(search.previous());
        search.found().forEach(c -> points.add(pointOf(c.restaurant())));
        TravelMatrix matrix = travelTimeService.buildMatrix(points,
                target.options().mode(), target.options().walkThresholdMinutes());

        List<CandidateDto> candidates = new ArrayList<>();
        for (int i = 0; i < search.found().size(); i++) {
            RestaurantCandidate c = search.found().get(i);
            Restaurant r = c.restaurant();
            TravelLeg leg = matrix.get(0, i + 1);
            candidates.add(new CandidateDto(r.getId(), r.getName(), r.getGenre(), r.getRatingVal(), r.getReviewCnt(),
                    budgetOf(r, search.meal()), r.getAddress(), r.getNearestStation(),
                    lat(r.getLocation()), lng(r.getLocation()), c.distanceMeters(),
                    new ItineraryResponseDto.TravelDto(leg.mode(), leg.minutes(), leg.distanceMeters(), leg.estimated()),
                    overlaps(previousMeal, r.getGenre()), c.budgetRelaxed(),
                    r.getHoliday(), RegularHoliday.isClosedOn(r.getHoliday(), visitDate),
                    r.getNameKo(), GenreLabels.of(r.getGenre())));
        }
        return new StopCandidatesResponseDto(target.stop().getId(), target.stop().getType(),
                search.maxYen(), candidates);
    }

    // ===================== 교체 =====================

    public ItineraryResponseDto replace(Long userId, Long tripId, Long stopId, Long restaurantId) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("이미 일정을 만들거나 고치고 있어요. 잠시만 기다려주세요.");
        }
        try {
            // 고른 식당이 아직 Google에서 확인 전이면 먼저 확인한다 (트랜잭션 밖, 1건). 폐업 의심이면 아래 doReplace에서 막힌다.
            // Google이 실패하면 기록 없이 통과 (일정 생성과 같은 원칙)
            restaurantMatchService.verify(Set.of(restaurantId));
            return writeTransaction.execute(status -> doReplace(userId, tripId, stopId, restaurantId));
        } finally {
            lock.release(tripId);
        }
    }

    private ItineraryResponseDto doReplace(Long userId, Long tripId, Long stopId, Long restaurantId) {
        Target target = load(userId, tripId, stopId);
        ItineraryStop stop = target.stop();

        if (Objects.equals(stop.getRestaurant().getId(), restaurantId)) {
            throw new IllegalArgumentException("이미 이 식당으로 정해져 있어요.");
        }
        // Google에서 찾지 못했거나 폐업으로 나온 식당은 후보였더라도(저장된 대안 포함) 넣지 않는다
        Restaurant requested = restaurantRepository.findById(restaurantId).orElse(null);
        if (requested != null && requested.isExcludedByGoogleMatch()) {
            throw new IllegalArgumentException(CLOSED_RESTAURANT_MESSAGE);
        }
        RestaurantCandidate chosen = findChosen(target, restaurantId);
        Restaurant replacement = chosen.restaurant();

        // 바뀐 식당 기준으로 모든 끼니의 "앞 끼니와 같은 음식" 표시를 다시 판단한다 (다음 끼니도 달라질 수 있다)
        Map<ItineraryStop, Boolean> repeated = genreRepeatedFlags(target.latest(), stop, replacement);

        Itinerary latest = target.latest();
        Itinerary next = Itinerary.create(target.trip(), latest.getVersionNo() + 1, latest.getKrwPerYen(),
                ItinerarySource.EDITED, replaceNote(target.day(), stop, replacement)).copyExchangeRateFrom(latest);
        TravelPoint hotel = hotelPoint(target.trip());
        for (ItineraryDay day : latest.getDays()) {
            boolean edited = day.getDayNumber() == target.day().getDayNumber();
            next.addDay(edited
                    ? retimedDay(day, stop, chosen, repeated, hotel, target.options())
                    : copyDay(day, repeated));
        }

        saveNewVersion(next);
        target.trip().allowRestaurant(replacement.getId());   // 예전에 뺐던 곳을 다시 고르면(대안 목록 등) 빼기 해제
        target.trip().excludeRestaurant(stop.getRestaurant().getId());   // 바꾼 식당은 이 여행에서 다시 추천하지 않는다
        target.trip().markEdited();

        log.info("[Itinerary] 식당 교체 tripId={} version={} DAY{} {} → {}", tripId, next.getVersionNo(),
                target.day().getDayNumber(), stop.getRestaurant().getName(), replacement.getName());
        return toResponse(next);
    }

    /** 고른 식당이 이 자리의 후보인지 확인한다. 일정을 만들 때 저장해둔 대안은 후보로 인정한다 */
    private RestaurantCandidate findChosen(Target target, Long restaurantId) {
        Search search = search(target, VERIFY_LIMIT);
        RestaurantCandidate found = search.found().stream()
                .filter(c -> Objects.equals(c.restaurant().getId(), restaurantId))
                .findFirst().orElse(null);
        if (found != null) {
            return found;
        }
        // 원하는 장르로 넓혀 찾은 후보(채팅 수정)도 같은 규칙으로 다시 찾아서 인정한다
        Restaurant requested = restaurantRepository.findById(restaurantId).orElse(null);
        if (requested != null) {
            RestaurantCandidate wanted = searchOne(target, RestaurantFinder.genresOf(requested.getGenre()), restaurantId);
            if (wanted != null) {
                return wanted;
            }
        }
        if (target.stop().getAlternativeRestaurantIds().contains(restaurantId)) {
            Restaurant saved = restaurantRepository.findById(restaurantId).orElse(null);
            if (saved != null) {
                return new RestaurantCandidate(saved, 0, 0, false);
            }
        }
        throw new IllegalArgumentException("이 자리에 넣을 수 없는 식당이에요. 후보 목록에서 골라주세요.");
    }

    /** 변경 이력에 보여줄 한 줄: "DAY2 점심: ザ・カリ → つじ田" */
    private static String replaceNote(ItineraryDay day, ItineraryStop stop, Restaurant replacement) {
        String meal = stop.getType() == StopType.LUNCH ? "점심" : "저녁";
        return "DAY" + day.getDayNumber() + " " + meal + ": " + stop.getRestaurant().getName()
                + " → " + replacement.getName();
    }

    // ===================== 여행 조건 변경 =====================

    /**
     * 숙소·이동수단·이동 제약처럼 장소는 그대로 두고 이동 시간만 달라지는 조건이 바뀌었을 때,
     * 최신 일정의 모든 날 시각을 다시 계산해서 새 버전(EDITED)으로 저장한다.
     * AI를 부르지 않으므로 하루 생성 횟수에 들어가지 않고, 사용자가 바꾼 식당·관광지도 그대로 남는다.
     * 숙소 좌표는 호출하는 쪽이 비워두면 여기서 다시 찾는다.
     *
     * @return 새 버전 일정. 아직 만든 일정이 없으면 빈 값
     */
    public Optional<ItineraryResponseDto> retimeForConditions(Long userId, Long tripId, String changeNote) {
        return retimeForConditions(userId, tripId, changeNote, false);
    }

    /** @param keepAwaiting 조건을 바꾸기 전 여행이 확인 대기(완성 전)였으면 true → 다시 확인 대기로 둔다 */
    public Optional<ItineraryResponseDto> retimeForConditions(Long userId, Long tripId, String changeNote,
                                                              boolean keepAwaiting) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("이미 일정을 만들거나 고치고 있어요. 잠시만 기다려주세요.");
        }
        try {
            return writeTransaction.execute(status -> doRetimeForConditions(userId, tripId, changeNote, keepAwaiting));
        } finally {
            lock.release(tripId);
        }
    }

    private Optional<ItineraryResponseDto> doRetimeForConditions(Long userId, Long tripId, String changeNote,
                                                                  boolean keepAwaiting) {
        Trip trip = findTrip(userId, tripId);
        Itinerary latest = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId).orElse(null);
        if (latest == null) {
            return Optional.empty();
        }

        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);
        TravelOptions options = TravelOptions.of(trip, ItineraryPlanner.constraintsOf(preference), travelProperties);
        TravelPoint hotel = hotelPoint(trip);

        Itinerary next = Itinerary.create(trip, latest.getVersionNo() + 1, latest.getKrwPerYen(),
                ItinerarySource.EDITED, changeNote).copyExchangeRateFrom(latest);
        for (ItineraryDay day : latest.getDays()) {
            next.addDay(retimedDay(day, null, null, Map.of(), hotel, options));
        }

        saveNewVersion(next);
        // 조건 저장 때 READY_TO_GENERATE로 바꿔 두었다. 원래 확인 대기(완성 전)였으면 다시 확인 대기로 (10-12)
        trip.setStatus(keepAwaiting ? TripStatus.AWAITING_CONFIRMATION : TripStatus.EDITING);

        log.info("[Itinerary] 여행 조건 변경으로 시각 재계산 tripId={} 새 버전 {} ({})",
                tripId, next.getVersionNo(), changeNote);
        return Optional.of(toResponse(next));
    }

    // ===================== 새 버전 만들기 =====================

    /**
     * 순서는 그대로 두고 시각만 다시 계산한 하루.
     * - 식당 교체: target 자리에 chosen 식당을 넣고, 설명은 식당 이름이 달라져서 비운다
     * - 여행 조건 변경(target·chosen이 null): 장소는 그대로라 설명도 그대로 둔다 (설명에는 숫자 시각이 없다)
     */
    private ItineraryDay retimedDay(ItineraryDay day, ItineraryStop target, RestaurantCandidate chosen,
                                    Map<ItineraryStop, Boolean> repeated, TravelPoint hotel, TravelOptions options) {
        Restaurant replacement = chosen == null ? null : chosen.restaurant();
        List<ItineraryStop> stops = day.getStops();

        List<RetimeItem> items = new ArrayList<>();
        for (ItineraryStop s : stops) {
            items.add(new RetimeItem(s.getType(), s == target ? pointOf(replacement) : pointOf(s), s.getStayMinutes()));
        }
        Retimed retimed = dayScheduler.retime(items, day.getWindowStart(), day.getWindowEnd(), hotel, options);

        // 시간 안내는 새로 계산한 것으로 바꾼다. 휴일 안내는 식당이 바뀔 때만 다시 판단한다 (날짜는 그대로)
        List<String> warnings = day.getWarnings().stream()
                .filter(w -> !isTimeWarning(w) && (replacement == null || !isHolidayWarning(w)))
                .collect(Collectors.toCollection(ArrayList::new));
        warnings.addAll(retimed.warnings());
        // 사용자가 정기 휴일인 식당을 골랐으면 막지는 않고 알려준다 (임시 휴무와 공휴일은 알 수 없다)
        if (replacement != null && RegularHoliday.isClosedOn(replacement.getHoliday(), day.getDate())) {
            warnings.add(replacement.getName() + "은(는) 이 날이 정기 휴일이에요. 영업하는지 확인해주세요.");
        }

        ItineraryDay copy = ItineraryDay.create(day.getDayNumber(), day.getDate(), day.getZone(),
                day.getWindowStart(), day.getWindowEnd(), replacement == null ? day.getDescription() : null,
                new ArrayList<>(day.getDropped()), warnings);

        for (int i = 0; i < stops.size(); i++) {
            ItineraryStop s = stops.get(i);
            RetimedStop timed = retimed.stops().get(i);
            boolean isTarget = s == target;
            copy.addStop(ItineraryStop.builder()
                    .stopOrder(s.getStopOrder())
                    .type(s.getType())
                    .attraction(s.getAttraction())
                    .restaurant(isTarget ? replacement : s.getRestaurant())
                    .arrivalTime(timed.arrival())
                    .departureTime(timed.departure())
                    .stayMinutes(s.getStayMinutes())
                    .waitMinutes(timed.waitMinutes())
                    .travelMode(timed.travelIn().mode())
                    .travelMinutes(timed.travelIn().minutes())
                    .travelDistanceMeters(timed.travelIn().distanceMeters())
                    .travelEstimated(timed.travelIn().estimated())
                    .budgetRelaxed(isTarget ? chosen.budgetRelaxed() : s.isBudgetRelaxed())
                    .genreRepeated(repeated.getOrDefault(s, s.isGenreRepeated()))
                    .alternativeRestaurantIds(isTarget ? alternativesAfterReplace(s, replacement) : s.getAlternativeRestaurantIds())
                    .build());
        }
        return copy;
    }

    /** 바뀌지 않은 날: 그대로 복사 (끼니의 "같은 음식" 표시만 다시 판단한 값으로) */
    private ItineraryDay copyDay(ItineraryDay day, Map<ItineraryStop, Boolean> repeated) {
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
                    .genreRepeated(repeated.getOrDefault(s, s.isGenreRepeated()))
                    .alternativeRestaurantIds(s.getAlternativeRestaurantIds())
                    .build());
        }
        return copy;
    }

    /** 방금 바꾼 식당으로 되돌릴 수 있게 이전 식당을 맨 앞 대안으로 두고, 새로 고른 식당은 대안에서 뺀다 */
    private static List<Long> alternativesAfterReplace(ItineraryStop stop, Restaurant replacement) {
        List<Long> alternatives = new ArrayList<>();
        alternatives.add(stop.getRestaurant().getId());
        stop.getAlternativeRestaurantIds().stream()
                .filter(id -> !Objects.equals(id, replacement.getId()))
                .forEach(alternatives::add);
        return alternatives.stream().limit(MAX_ALTERNATIVES).toList();
    }

    /**
     * 모든 끼니의 "바로 앞 끼니와 같은 음식" 표시를 다시 판단한다 (대표 장르가 겹치면 true).
     * 일정을 만들 때와 같은 기준이고, 날짜를 넘어서 이어진다.
     */
    private static Map<ItineraryStop, Boolean> genreRepeatedFlags(Itinerary itinerary, ItineraryStop target,
                                                                  Restaurant replacement) {
        Map<ItineraryStop, Boolean> flags = new IdentityHashMap<>();
        Set<String> previous = Set.of();
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop s : day.getStops()) {
                if (s.getRestaurant() == null) {
                    continue;
                }
                Restaurant r = s == target ? replacement : s.getRestaurant();
                Set<String> main = RestaurantFinder.mainGenres(r.getGenre());
                flags.put(s, !Collections.disjoint(main, previous));
                previous = main;
            }
        }
        return flags;
    }

    private static boolean isHolidayWarning(String warning) {
        return warning.contains("정기 휴일");
    }

    private static boolean isTimeWarning(String warning) {
        return warning.startsWith("점심 시간이") || warning.startsWith("저녁 시간이") || warning.startsWith("일정이 ");
    }

    // ===================== 후보 검색 =====================

    private Search search(Target target, int limit) {
        return search(target, limit, Set.of());
    }

    /** 고른 식당 하나가 원하는 장르 넓게 찾기의 후보가 되는지 (없으면 null) */
    private RestaurantCandidate searchOne(Target target, Set<String> wantedGenres, Long restaurantId) {
        Meal meal = target.stop().getType() == StopType.LUNCH ? Meal.LUNCH : Meal.DINNER;
        TravelPoint anchor = anchorOf(target);
        return restaurantFinder.findWantedOne(anchor.lat(), anchor.lng(), meal, target.budget().of(meal),
                target.filter(), excludedIds(target), wantedGenres, restaurantId).orElse(null);
    }

    /** 끼니를 찾는 기준점: 일정을 만들 때처럼 앞뒤 장소의 중간. 앞뒤가 없으면 있는 쪽, 둘 다 없으면 지금 식당 */
    private TravelPoint anchorOf(Target target) {
        List<ItineraryStop> stops = target.day().getStops();
        TravelPoint before = target.index() > 0 ? pointOf(stops.get(target.index() - 1)) : null;
        TravelPoint after = target.index() + 1 < stops.size() ? pointOf(stops.get(target.index() + 1)) : null;
        if (before != null && after != null) {
            return new TravelPoint("끼니 기준(중간)", (before.lat() + after.lat()) / 2, (before.lng() + after.lng()) / 2);
        }
        if (before != null) return before;
        if (after != null) return after;
        return pointOf(target.stop());
    }

    private Search search(Target target, int limit, Set<String> wantedGenres) {
        Meal meal = target.stop().getType() == StopType.LUNCH ? Meal.LUNCH : Meal.DINNER;
        int maxYen = target.budget().of(meal);

        List<ItineraryStop> stops = target.day().getStops();
        TravelPoint before = target.index() > 0 ? pointOf(stops.get(target.index() - 1)) : null;
        TravelPoint after = target.index() + 1 < stops.size() ? pointOf(stops.get(target.index() + 1)) : null;

        // 일정을 만들 때처럼 앞뒤 장소의 중간에서 찾는다. 앞뒤가 없으면 있는 쪽, 둘 다 없으면 지금 식당 근처
        TravelPoint anchor;
        if (before != null && after != null) {
            anchor = new TravelPoint("끼니 기준(중간)", (before.lat() + after.lat()) / 2, (before.lng() + after.lng()) / 2);
        } else if (before != null) {
            anchor = before;
        } else if (after != null) {
            anchor = after;
        } else {
            anchor = pointOf(target.stop());
        }
        TravelPoint previous = before != null ? before : hotelPoint(target.trip());

        List<RestaurantCandidate> found = wantedGenres == null || wantedGenres.isEmpty()
                ? restaurantFinder.find(anchor.lat(), anchor.lng(), meal, maxYen,
                        target.filter(), excludedIds(target), limit)
                : restaurantFinder.findWanted(anchor.lat(), anchor.lng(), meal, maxYen,
                        target.filter(), excludedIds(target), limit, wantedGenres);
        return new Search(meal, maxYen, previous, found);
    }

    /** 후보에서 뺄 식당: 일정에 이미 있는 곳 + 사용자가 이 여행에서 바꾼 곳 (10-12) */
    private static Set<Long> excludedIds(Target target) {
        Set<Long> ids = new java.util.HashSet<>(usedRestaurantIds(target.latest()));
        ids.addAll(target.trip().excludedRestaurants());
        return ids;
    }

    private static Set<Long> usedRestaurantIds(Itinerary itinerary) {
        return itinerary.getDays().stream()
                .flatMap(day -> day.getStops().stream())
                .filter(s -> s.getRestaurant() != null)
                .map(s -> s.getRestaurant().getId())
                .collect(Collectors.toSet());
    }

    /** 이 끼니 바로 앞 끼니의 대표 장르 (날짜를 넘어서 이어진다) */
    private static Set<String> previousMealGenres(Itinerary itinerary, ItineraryStop target) {
        Set<String> last = Set.of();
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop s : day.getStops()) {
                if (s == target) {
                    return last;
                }
                if (s.getRestaurant() != null) {
                    last = RestaurantFinder.mainGenres(s.getRestaurant().getGenre());
                }
            }
        }
        return last;
    }

    private static boolean overlaps(Set<String> previousMain, String genreCsv) {
        return !Collections.disjoint(RestaurantFinder.mainGenres(genreCsv), previousMain);
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
        if (stop.getType() == StopType.ATTRACTION) {
            throw new IllegalArgumentException("식당(점심·저녁) 항목만 바꿀 수 있어요.");
        }

        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);
        var constraints = ItineraryPlanner.constraintsOf(preference);
        TravelOptions options = TravelOptions.of(trip, constraints, travelProperties);
        double foodRatio = preference == null ? DEFAULT_FOOD_RATIO : preference.getFoodBudgetRatio();
        // 교체 후보의 예산은 그 일정을 만들 때 쓴 환율로 (10-12: 환율이 날마다 바뀌어도 같은 일정 안에서는 같게)
        MealBudget budget = mealBudgetCalculator.calculate(trip.getBudget(), trip.getTravelers(),
                ItineraryPlanner.daysOf(trip), foodRatio, latest.getKrwPerYen());
        FoodFilter filter = foodFilterResolver.resolve(constraints, trip.getAllergies());

        return new Target(trip, latest, day, stop, day.getStops().indexOf(stop), options, filter, budget);
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

    // ===================== 좌표·표시 =====================

    private TravelPoint hotelPoint(Trip trip) {
        Point hotel = accommodationLocator.locate(trip);
        return new TravelPoint(trip.getAccommodationName(), hotel.getY(), hotel.getX());
    }

    private static TravelPoint pointOf(ItineraryStop stop) {
        return stop.getType() == StopType.ATTRACTION
                ? new TravelPoint(stop.getAttraction().getName(),
                stop.getAttraction().getLocation().getY(), stop.getAttraction().getLocation().getX())
                : pointOf(stop.getRestaurant());
    }

    private static TravelPoint pointOf(Restaurant r) {
        return new TravelPoint(r.getName(), r.getLocation().getY(), r.getLocation().getX());
    }

    // 점심이면 점심 예산, 저녁이면 저녁 예산
    private static String budgetOf(Restaurant r, Meal meal) {
        return meal == Meal.LUNCH ? r.getBudgetLunch() : r.getBudgetDinner();
    }

    // JTS 좌표는 (경도 x, 위도 y) 순서
    private static Double lat(Point point) {
        return point == null ? null : point.getY();
    }

    private static Double lng(Point point) {
        return point == null ? null : point.getX();
    }

    // ===================== 내부 자료 =====================

    /** 바꿀 항목과 그 주변 (여행, 최신 일정, 그날, 항목, 그날 안에서의 위치, 이 여행의 이동·예산·취향) */
    private record Target(Trip trip, Itinerary latest, ItineraryDay day, ItineraryStop stop, int index,
                          TravelOptions options, FoodFilter filter, MealBudget budget) {
    }

    private record Search(Meal meal, int maxYen, TravelPoint previous, List<RestaurantCandidate> found) {
    }
}