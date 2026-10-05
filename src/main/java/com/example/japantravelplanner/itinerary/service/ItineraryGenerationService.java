package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.meal.MealProperties;
import com.example.japantravelplanner.itinerary.narration.ItineraryNarrator;
import com.example.japantravelplanner.itinerary.narration.TripBrief;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
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
 *  ② 설명 (트랜잭션 밖): Terra가 하루 설명을 쓴다. 실패해도 일정은 정상
 *  ③ 저장 (트랜잭션): 새 버전으로 저장하고 여행 상태를 GENERATED로
 *
 * 다시 만들면 새 버전이 쌓인다. 같은 여행을 동시에 만들 수 없고, 여행당 24시간에 5번까지 만들 수 있다.
 */
@Slf4j
@Service
public class ItineraryGenerationService {

    static final int DAILY_GENERATION_LIMIT = 5;

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

            // ② 설명: 트랜잭션 밖에서
            Map<Integer, String> descriptions = narrator.narrate(
                    planned.brief(), planned.result().days(), typeLabels::toVisibleLabels);

            // ③ 저장
            return writeTransaction.execute(status -> save(userId, tripId, planned, descriptions));
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
            // 같은 버전 번호로 동시에 저장하려 한 경우 (잠금은 서버 한 대 안에서만 막아준다)
            throw new ConflictException("일정이 동시에 만들어졌어요. 잠시 후 다시 확인해주세요.");
        }
        trip.setStatus(TripStatus.GENERATED);

        log.info("[Itinerary] 저장 tripId={} version={} 일수={} 설명={}일", tripId, version,
                planned.result().days().size(), descriptions.size());
        return toResponse(itinerary);
    }

    // ===================== 조회 =====================

    /** 가장 최근 버전의 일정 */
    public ItineraryResponseDto getLatest(Long userId, Long tripId) {
        return readTransaction.execute(status -> {
            findTrip(userId, tripId);
            Itinerary itinerary = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                    .orElseThrow(() -> new NotFoundException("아직 만들어진 일정이 없습니다."));
            return ItineraryResponseDto.from(itinerary, typeLabels::toVisibleLabels);
        });
    }

    // 식당 대안 후보는 id만 저장되어 있어서, 일정 전체의 대안을 한 번에 조회해서 풀어준다
    private ItineraryResponseDto toResponse(Itinerary itinerary) {
        Set<Long> ids = ItineraryResponseDto.alternativeRestaurantIds(itinerary);
        Map<Long, Restaurant> alternatives = ids.isEmpty() ? Map.of()
                : restaurantRepository.findAllById(ids).stream()
                  .collect(Collectors.toMap(Restaurant::getId, Function.identity()));
        return ItineraryResponseDto.from(itinerary, typeLabels::toVisibleLabels, alternatives);
    }

    private Trip findTrip(Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
    }

    /** ① 단계 결과: 계산된 일정과 설명을 쓸 때 필요한 여행 정보 */
    private record Planned(ItineraryPlanner.Result result, TripBrief brief) {
    }
}