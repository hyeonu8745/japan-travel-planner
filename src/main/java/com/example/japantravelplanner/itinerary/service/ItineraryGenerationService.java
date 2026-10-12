package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryConfirmResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.meal.MealProperties;
import com.example.japantravelplanner.itinerary.narration.ItineraryNarrator;
import com.example.japantravelplanner.itinerary.narration.TripBrief;
import com.example.japantravelplanner.itinerary.naming.KoreanNameService;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.ScheduledStop;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.match.RestaurantMatchService;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;
import com.example.japantravelplanner.chat.repository.ChatMessageRepository;
import com.example.japantravelplanner.itinerary.reason.PlanSummary;
import com.example.japantravelplanner.itinerary.reason.ReasonContext;
import com.example.japantravelplanner.itinerary.reason.ReasonContextLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일정을 만들어서 저장한다.
 *
 * 흐름은 ChatService와 같은 원칙으로 세 단계로 나눈다. (AI 호출은 수십 초가 걸릴 수 있어서 DB 연결을 붙잡지 않는다)
 *  ① 계산 (트랜잭션): 여행 확인, 횟수 제한, 숙소 좌표, 일정 계산. 외부 AI 호출 없음
 *  ①-1 식당 확인 (트랜잭션 밖): 고른 식당이 Google에 있는지 확인. 폐업 등으로 빠진 곳이 있으면 ①을 다시
 *  ② 설명 (트랜잭션 밖): Terra가 하루 설명을 쓴다. 실패해도 일정은 정상
 *  ③ 저장 (트랜잭션): 새 버전으로 저장하고 여행 상태를 GENERATED로. 채팅에 "이렇게 짰어요" 요약을 남긴다 (서버가 쓰는 글, AI 아님)
 *
 * 다시 만들면 새 버전이 쌓인다. 같은 여행을 동시에 만들 수 없고, 여행당 24시간에 5번까지 만들 수 있다.
 */
@Slf4j
@Service
public class ItineraryGenerationService {

    static final int DAILY_GENERATION_LIMIT = 5;

    /** Itinerary의 (여행, 버전 번호) 유일 제약 이름 */
    private static final String VERSION_UNIQUE_CONSTRAINT = "uk_itineraries_trip_version";

    // 여행 하나당 24시간 안에 AI로 만들 수 있는 횟수 (AI 비용 대책). 요금제별로 바꿀 수 있게 설정으로 뺐다
    @Value("${app.itinerary.daily-generation-limit:5}")
    private int dailyGenerationLimit = DAILY_GENERATION_LIMIT;

    private final TripRepository tripRepository;
    private final TripPreferenceRepository preferenceRepository;
    private final ItineraryRepository itineraryRepository;
    private final AttractionRepository attractionRepository;
    private final RestaurantRepository restaurantRepository;
    private final AccommodationLocator accommodationLocator;
    private final ItineraryPlanner planner;
    private final ItineraryNarrator narrator;
    private final PlaceTypeLabels typeLabels;
    private final MealProperties mealProperties;
    private final GenerationLock lock;
    private final RestaurantMatchService restaurantMatchService;
    private final ReasonContextLoader reasonContextLoader;
    private final ChatMessageRepository chatMessageRepository;
    private final KoreanNameService koreanNames;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryGenerationService(TripRepository tripRepository,
                                      TripPreferenceRepository preferenceRepository,
                                      ItineraryRepository itineraryRepository,
                                      AttractionRepository attractionRepository,
                                      RestaurantRepository restaurantRepository,
                                      AccommodationLocator accommodationLocator,
                                      ItineraryPlanner planner,
                                      ItineraryNarrator narrator,
                                      PlaceTypeLabels typeLabels,
                                      MealProperties mealProperties,
                                      GenerationLock lock,
                                      RestaurantMatchService restaurantMatchService,
                                      ReasonContextLoader reasonContextLoader,
                                      ChatMessageRepository chatMessageRepository,
                                      KoreanNameService koreanNames,
                                      PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.preferenceRepository = preferenceRepository;
        this.itineraryRepository = itineraryRepository;
        this.attractionRepository = attractionRepository;
        this.restaurantRepository = restaurantRepository;
        this.accommodationLocator = accommodationLocator;
        this.planner = planner;
        this.narrator = narrator;
        this.typeLabels = typeLabels;
        this.mealProperties = mealProperties;
        this.lock = lock;
        this.restaurantMatchService = restaurantMatchService;
        this.reasonContextLoader = reasonContextLoader;
        this.chatMessageRepository = chatMessageRepository;
        this.koreanNames = koreanNames;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 생성 =====================

    public ItineraryResponseDto generate(Long userId, Long tripId) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("이미 일정을 만들고 있어요. 잠시만 기다려주세요.");
        }
        try {
            // ① 계산 (숙소 좌표를 처음 찾으면 여행에 저장하므로 쓰기 트랜잭션)
            Planned planned = writeTransaction.execute(status -> plan(userId, tripId));

            // ①-1 고른 식당을 Google에서 확인 (트랜잭션 밖). 찾지 못함·폐업이 나오면 그 식당은 후보에서 빠지므로 다시 계산한다.
            // 마지막으로 다시 계산해서 새로 고른 식당은 확인 전이라도 통과 (다음 생성 때 확인)
            for (int round = 0; round < restaurantMatchService.verifyRounds(); round++) {
                int excluded = restaurantMatchService.verify(chosenRestaurantIds(planned.result()));
                if (excluded == 0) break;
                log.info("[Itinerary] 폐업 의심 식당 {}곳을 빼고 다시 계산 tripId={}", excluded, tripId);
                planned = writeTransaction.execute(status -> plan(userId, tripId));
            }

            // ①-2 한국어 이름: 처음 쓰는 장소·식당만 AI로 만들어 저장 (트랜잭션 밖). 설명 AI가 같은 이름을 쓰게 설명보다 먼저
            koreanNames.ensure(attractionsOf(planned.result()), restaurantsOf(planned.result()));

            // ② 설명: 트랜잭션 밖에서
            Planned finalPlan = planned;
            Map<Integer, String> descriptions = narrator.narrate(
                    finalPlan.brief(), finalPlan.result().days(), typeLabels::toVisibleLabels);

            // ③ 저장
            return writeTransaction.execute(status -> save(userId, tripId, finalPlan, descriptions));
        } finally {
            lock.release(tripId);
        }
    }

    private Planned plan(Long userId, Long tripId) {
        Trip trip = findTrip(userId, tripId);

        long recent = itineraryRepository.countByTripIdAndSourceAndCreatedAtAfter(
                tripId, ItinerarySource.GENERATED, LocalDateTime.now().minusHours(24));
        if (recent >= dailyGenerationLimit) {
            throw new TooManyRequestsException("오늘은 이 여행의 일정을 더 만들 수 없어요. 내일 다시 시도해주세요.");
        }

        TripPreference preference = preferenceRepository.findByTripId(tripId).orElse(null);
        Point hotel = accommodationLocator.locate(trip);
        ItineraryPlanner.Result result = planner.plan(trip, preference, hotel);

        // 트랜잭션이 끝난 뒤에도 쓸 수 있게 지연 로딩 컬렉션을 복사해둔다
        List<PreferenceConstraint> constraints = List.copyOf(ItineraryPlanner.constraintsOf(preference));
        TripBrief brief = new TripBrief(trip.getTravelers(), trip.getTravelPace(),
                List.copyOf(ItineraryPlanner.interestsOf(trip)), constraints);
        return new Planned(result, brief);
    }

    private ItineraryResponseDto save(Long userId, Long tripId, Planned planned, Map<Integer, String> descriptions) {
        Trip trip = findTrip(userId, tripId);
        int version = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .map(latest -> latest.getVersionNo() + 1)
                .orElse(1);

        Itinerary itinerary = ItineraryMapper.toEntity(trip, version, mealProperties.getKrwPerYen(),
                planned.result().days(), planned.result().windows(), descriptions,
                attractionRepository::getReferenceById, restaurantRepository::getReferenceById);

        try {
            itineraryRepository.saveAndFlush(itinerary);
        } catch (DataIntegrityViolationException e) {
            // 같은 버전 번호로 동시에 저장하려 한 경우만 409로 안내한다 (잠금은 서버 한 대 안에서만 막아준다).
            // 그 밖의 DB 거절(허용 값 제약 등)은 동시성 문제가 아니므로 원인을 로그에 남기고 그대로 던진다.
            // 교체·되돌리기와 같은 기준이다 (예전에 전부 409로 바꿔서 CHECK 제약 문제의 원인이 가려졌다)
            Throwable cause = e.getMostSpecificCause();
            String message = cause.getMessage() == null ? "" : cause.getMessage();
            if (message.contains(VERSION_UNIQUE_CONSTRAINT)) {
                throw new ConflictException("일정이 동시에 만들어졌어요. 잠시 후 다시 확인해주세요.");
            }
            log.error("[Itinerary] 일정 저장이 DB에서 거절됨 (동시성 문제가 아님): {}", message);
            throw e;
        }
        // AI로 새로 만든 일정은 사용자가 [완성]을 누를 때까지 확인 대기 (10-12, 프론트 요청)
        trip.setStatus(TripStatus.AWAITING_CONFIRMATION);

        // 어떻게 짰는지 채팅에 남긴다 (일정이 만들어진 뒤에는 채팅 입력이 막혀 있어서 기록으로만 보인다)
        ReasonContext reasons = reasonContextLoader.load(trip);
        chatMessageRepository.save(ChatMessage.builder().trip(trip).role(ChatRole.ASSISTANT)
                .content(PlanSummary.write(itinerary, reasons)).build());

        log.info("[Itinerary] 저장 tripId={} version={} 일수={} 설명={}일", tripId, version,
                planned.result().days().size(), descriptions.size());
        return toResponse(itinerary, reasons);
    }

    // ===================== 조회 =====================

    /** 가장 최근 버전의 일정 */
    public ItineraryResponseDto getLatest(Long userId, Long tripId) {
        return readTransaction.execute(status -> {
            findTrip(userId, tripId);
            Itinerary itinerary = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                    .orElseThrow(() -> new NotFoundException("아직 만들어진 일정이 없습니다."));
            return ItineraryResponseDto.from(itinerary, typeLabels::toVisibleLabels, Map.of(),
                    reasonContextLoader.load(itinerary.getTrip()));
        });
    }

    // ===================== 완성 확인 =====================

    /**
     * 사용자가 [완성]을 눌렀다 (10-12, 프론트 요청). 확인 대기(AWAITING_CONFIRMATION)면 GENERATED로 바꾸고 가장 최근 일정을 돌려준다.
     * 이미 GENERATED·EDITING이면 바꾸지 않고 같은 응답 (여러 번 눌러도 같은 결과).
     * 일정이 없거나, 조건을 바꾼 뒤 일정을 다시 맞추지 못한 상태(READY_TO_GENERATE 등)면 409
     */
    public ItineraryConfirmResponseDto confirm(Long userId, Long tripId) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("일정을 만들거나 고치는 중이에요. 끝난 뒤에 다시 눌러 주세요.");
        }
        try {
            return writeTransaction.execute(status -> {
                Trip trip = findTrip(userId, tripId);
                Itinerary latest = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                        .orElseThrow(() -> new ConflictException("아직 만들어진 일정이 없어요. 일정을 먼저 만들어 주세요."));
                if (trip.isAwaitingConfirmation()) {
                    trip.setStatus(TripStatus.GENERATED);
                    log.info("[Itinerary] 완성 확인 tripId={} version={}", tripId, latest.getVersionNo());
                } else if (trip.getStatus() != TripStatus.GENERATED && trip.getStatus() != TripStatus.EDITING) {
                    throw new ConflictException("여행 조건이 바뀌어 일정을 다시 만들어야 해요. 일정을 다시 만든 뒤 완성해 주세요.");
                }
                return new ItineraryConfirmResponseDto(tripId, trip.getStatus(),
                        toResponse(latest, reasonContextLoader.load(trip)));
            });
        } finally {
            lock.release(tripId);
        }
    }

    // 식당 대안 후보는 id만 저장되어 있어서, 일정 전체의 대안을 한 번에 조회해서 풀어준다
    private ItineraryResponseDto toResponse(Itinerary itinerary, ReasonContext reasons) {
        Set<Long> ids = ItineraryResponseDto.alternativeRestaurantIds(itinerary);
        Map<Long, Restaurant> alternatives = ids.isEmpty() ? Map.of()
                : restaurantRepository.findAllById(ids).stream()
                  .collect(Collectors.toMap(Restaurant::getId, Function.identity()));
        return ItineraryResponseDto.from(itinerary, typeLabels::toVisibleLabels, alternatives, reasons);
    }

    private Trip findTrip(Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
    }

    /** 일정에 들어간 식당 (다음 후보는 빼고 실제로 고른 곳만) */
    static Set<Long> chosenRestaurantIds(ItineraryPlanner.Result result) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ScheduledDay day : result.days()) {
            for (ScheduledStop stop : day.stops()) {
                if (stop.meal() != null && stop.meal().found()) {
                    ids.add(stop.meal().chosen().restaurant().getId());
                }
            }
        }
        return ids;
    }

    static List<Attraction> attractionsOf(ItineraryPlanner.Result result) {
        return result.days().stream().flatMap(day -> day.stops().stream())
                .filter(stop -> stop.attraction() != null)
                .map(stop -> stop.attraction().attraction()).toList();
    }

    /** 고른 식당과 다음 후보 (화면에 둘 다 이름이 보인다) */
    static List<Restaurant> restaurantsOf(ItineraryPlanner.Result result) {
        List<Restaurant> restaurants = new java.util.ArrayList<>();
        for (ScheduledDay day : result.days()) {
            for (ScheduledStop stop : day.stops()) {
                if (stop.meal() == null || !stop.meal().found()) continue;
                restaurants.add(stop.meal().chosen().restaurant());
                stop.meal().alternatives().forEach(c -> restaurants.add(c.restaurant()));
            }
        }
        return restaurants;
    }

    /** ① 단계 결과: 계산된 일정과 설명을 쓸 때 필요한 여행 정보 */
    private record Planned(ItineraryPlanner.Result result, TripBrief brief) {
    }
}