package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryVersionDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
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
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.repository.ItineraryStopRepository;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.match.RestaurantMatchService;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.japantravelplanner.itinerary.reason.ReasonContextLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

/** 변경 이력(버전 목록·조회)과 되돌리기. 교체로 쌓은 변경 설명도 확인한다 */
class ItineraryHistoryTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final RestaurantFinder finder = mock(RestaurantFinder.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();

    private final Trip trip = trip();
    private final Attraction a1 = attraction(11, "관광지1", "tourist_attraction");
    private final Attraction a2 = attraction(12, "관광지2", "tourist_attraction");
    private final Restaurant lunch1 = restaurant(21, "첫 점심집", "ラーメン");
    private final Restaurant dinner1 = restaurant(22, "첫 저녁집", "洋食");
    private final Restaurant lunch2 = restaurant(31, "바꾼 점심집", "うどん");
    private final Restaurant alt = restaurant(41, "대안 식당", "そば");

    private Itinerary v1;   // 처음 AI로 생성
    private Itinerary v2;   // 점심을 교체한 버전 (최신)
    private ItineraryEditService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(itineraryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        v1 = version(1, ItinerarySource.GENERATED, null, lunch1, "하루1 설명");
        v2 = version(2, ItinerarySource.EDITED, "DAY1 점심: 첫 점심집 → 바꾼 점심집", lunch2, null);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(v2));
        when(itineraryRepository.findByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(List.of(v2, v1));
        when(itineraryRepository.findByTripIdAndVersionNo(TRIP_ID, 1)).thenReturn(Optional.of(v1));
        when(itineraryRepository.findByTripIdAndVersionNo(TRIP_ID, 2)).thenReturn(Optional.of(v2));
        when(restaurantRepository.findAllById(anySet())).thenReturn(List.of(alt));

        PlaceTypeLabels labels = mock(PlaceTypeLabels.class);
        when(labels.toVisibleLabels(any())).thenReturn(List.of("관광명소"));
        TravelTimeService travel = new TravelTimeService(new TravelProperties(), null);
        service = new ItineraryEditService(tripRepository, mock(TripPreferenceRepository.class), itineraryRepository,
                mock(ItineraryStopRepository.class), restaurantRepository, mock(AccommodationLocator.class), finder,
                mock(MealBudgetCalculator.class), mock(FoodFilterResolver.class), travel, new TravelProperties(),
                new DayScheduler(travel), labels, lock, mock(RestaurantMatchService.class),
                mock(ReasonContextLoader.class), transactionManager);

        when(itineraryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 관광지1 → 점심 → 관광지2 → 저녁 하루 (점심 항목에는 대안 식당이 저장되어 있다) */
    private Itinerary version(int versionNo, ItinerarySource source, String note, Restaurant lunch, String description) {
        ScheduledDay day = day(1, List.of(
                        attractionStop(a1, "10:00", "10:40", 40),
                        mealStopWithAlternatives(StopType.LUNCH, lunch, List.of(alt), "12:00", "13:00"),
                        attractionStop(a2, "13:30", "14:10", 40),
                        mealStopWithAlternatives(StopType.DINNER, dinner1, List.of(), "18:00", "19:30")),
                List.of(), List.of());
        Map<Long, Attraction> attractions = Map.of(11L, a1, 12L, a2);
        Map<Long, Restaurant> restaurants = Map.of(21L, lunch1, 22L, dinner1, 31L, lunch2);
        Map<Integer, String> descriptions = description == null ? Map.of() : Map.of(1, description);
        Itinerary itinerary = ItineraryMapper.toEntity(trip, versionNo, 9.0 + versionNo, List.of(day),
                List.of(window("09:30", "22:00")), descriptions, attractions::get, restaurants::get);
        setField(itinerary, "source", source);   // 매퍼는 AI 생성으로 만든다. 교체한 버전처럼 보이도록 값을 채운다
        setField(itinerary, "changeNote", note);
        return itinerary;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Itinerary savedItinerary() {
        ArgumentCaptor<Itinerary> captor = ArgumentCaptor.forClass(Itinerary.class);
        verify(itineraryRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ── 목록과 조회 ──

    @Test
    void 버전_목록은_최신이_먼저이고_최신만_latest다() {
        List<ItineraryVersionDto> versions = service.versions(USER_ID, TRIP_ID);

        assertThat(versions).extracting(ItineraryVersionDto::version).containsExactly(2, 1);
        assertThat(versions).extracting(ItineraryVersionDto::latest).containsExactly(true, false);
        assertThat(versions).extracting(ItineraryVersionDto::source)
                .containsExactly(ItinerarySource.EDITED, ItinerarySource.GENERATED);
        assertThat(versions.get(0).changeNote()).isEqualTo("DAY1 점심: 첫 점심집 → 바꾼 점심집");
        assertThat(versions.get(1).changeNote()).isNull();   // 이 기능 이전에 만든 일정은 설명이 없다
    }

    @Test
    void 일정을_아직_안_만들었으면_빈_목록() {
        when(itineraryRepository.findByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(List.of());

        assertThat(service.versions(USER_ID, TRIP_ID)).isEmpty();
    }

    @Test
    void 남의_여행의_이력은_볼_수_없다() {
        assertThatThrownBy(() -> service.versions(999L, TRIP_ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.version(999L, TRIP_ID, 1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.restore(999L, TRIP_ID, 1)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 옛_버전도_그_버전의_내용_그대로_볼_수_있다() {
        ItineraryResponseDto old = service.version(USER_ID, TRIP_ID, 1);

        assertThat(old.version()).isEqualTo(1);
        assertThat(old.source()).isEqualTo(ItinerarySource.GENERATED);
        assertThat(old.days().get(0).description()).isEqualTo("하루1 설명");
        assertThat(old.days().get(0).stops().get(1).restaurant().id()).isEqualTo(21L);
    }

    @Test
    void 없는_버전은_찾을_수_없다() {
        assertThatThrownBy(() -> service.version(USER_ID, TRIP_ID, 9)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.restore(USER_ID, TRIP_ID, 9)).isInstanceOf(NotFoundException.class);
    }

    // ── 되돌리기 ──

    @Test
    void 되돌리면_옛_버전을_복사한_새_버전_RESTORED가_쌓인다() {
        ItineraryResponseDto response = service.restore(USER_ID, TRIP_ID, 1);

        Itinerary saved = savedItinerary();
        assertThat(saved.getVersionNo()).isEqualTo(3);
        assertThat(saved.getSource()).isEqualTo(ItinerarySource.RESTORED);
        assertThat(saved.getChangeNote()).isEqualTo("버전 1(으)로 되돌렸어요");
        assertThat(saved.getKrwPerYen()).isEqualTo(10.0);   // 옛 버전(v1)의 환율

        // 점심이 v1의 식당으로 돌아오고, 시각도 v1 그대로
        ItineraryStop lunch = saved.getDays().get(0).getStops().get(1);
        assertThat(lunch.getRestaurant().getId()).isEqualTo(21L);
        assertThat(lunch.getArrivalTime().toString()).isEqualTo("12:00");
        assertThat(lunch.getAlternativeRestaurantIds()).containsExactly(41L);   // 대안도 그 버전의 것

        assertThat(response.version()).isEqualTo(3);
        assertThat(response.source()).isEqualTo(ItinerarySource.RESTORED);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.EDITING);
    }

    @Test
    void 되돌리면_그_버전의_AI_설명도_돌아온다() {
        service.restore(USER_ID, TRIP_ID, 1);

        // v2에서는 교체로 비웠던 설명이 v1의 것으로 돌아온다 (v1의 식당과 맞는 설명이다)
        assertThat(savedItinerary().getDays().get(0).getDescription()).isEqualTo("하루1 설명");
    }

    @Test
    void 되돌려도_이전_버전들은_그대로다() {
        service.restore(USER_ID, TRIP_ID, 1);

        assertThat(v1.getVersionNo()).isEqualTo(1);
        assertThat(v2.getVersionNo()).isEqualTo(2);
        assertThat(v2.getDays().get(0).getStops().get(1).getRestaurant().getId()).isEqualTo(31L);
        verify(itineraryRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void 야스쿠니_같은_민감_장소가_든_옛_버전은_되돌리지_않는다() {
        setField(a1, "name", "靖国神社社殿");   // 필터(10-13) 전에 만든 버전

        assertThatThrownBy(() -> service.restore(USER_ID, TRIP_ID, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("되돌릴 수 없어요");
        assertThat(ItineraryEditService.hasSensitivePlace(v1)).isTrue();
    }

    @Test
    void 현재_버전으로_되돌릴_수_없다() {
        assertThatThrownBy(() -> service.restore(USER_ID, TRIP_ID, 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("현재 버전");
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 되돌리기_저장_충돌은_409로_안내하고_잠금은_풀린다() {
        when(itineraryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_itineraries_trip_version"));

        assertThatThrownBy(() -> service.restore(USER_ID, TRIP_ID, 1)).isInstanceOf(ConflictException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    @Test
    void 버전_번호_충돌이_아닌_DB_거절은_409로_바꾸지_않고_그대로_던진다() {
        // 예: enum 허용 값 제약(itineraries_source_check)에 걸린 경우. 동시성 문제로 오해하게 만들면 안 된다
        when(itineraryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "violates check constraint \"itineraries_source_check\""));

        assertThatThrownBy(() -> service.restore(USER_ID, TRIP_ID, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ConflictException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();   // 이 경우에도 잠금은 풀린다
    }

    @Test
    void 다른_작업_중이면_되돌릴_수_없고_그_잠금은_건드리지_않는다() {
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();

        assertThatThrownBy(() -> service.restore(USER_ID, TRIP_ID, 1)).isInstanceOf(ConflictException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isFalse();
    }

    @Test
    void 되돌려서_쌓인_버전은_하루_생성_횟수에_들어가지_않는_종류다() {
        // 횟수 제한은 GENERATED만 센다 (ItineraryGenerationService). RESTORED는 AI를 부르지 않는다
        service.restore(USER_ID, TRIP_ID, 1);

        assertThat(savedItinerary().getSource()).isNotEqualTo(ItinerarySource.GENERATED);
    }

    // ── 교체가 남기는 변경 설명 ──

    @Test
    void 식당을_바꾸면_무엇이_바뀌었는지_한_줄이_남는다() {
        ItineraryStopRepository stopRepository = mock(ItineraryStopRepository.class);
        PlaceTypeLabels labels = mock(PlaceTypeLabels.class);
        when(labels.toVisibleLabels(any())).thenReturn(List.of());
        TravelTimeService travel = new TravelTimeService(new TravelProperties(), null);
        MealBudgetCalculator budget = mock(MealBudgetCalculator.class);
        when(budget.calculate(anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble())).thenReturn(new MealBudget(3100, 5700));
        FoodFilterResolver filters = mock(FoodFilterResolver.class);
        when(filters.resolve(any(), any())).thenReturn(new FoodFilter(Set.of(), Set.of(), false));
        AccommodationLocator locator = mock(AccommodationLocator.class);
        when(locator.locate(trip)).thenReturn(GF.createPoint(new Coordinate(139.70, 35.69)));
        ItineraryEditService editService = new ItineraryEditService(tripRepository, mock(TripPreferenceRepository.class),
                itineraryRepository, stopRepository, restaurantRepository, locator, finder, budget, filters, travel,
                new TravelProperties(), new DayScheduler(travel), labels, lock, mock(RestaurantMatchService.class),
                mock(ReasonContextLoader.class), transactionManager);

        // 최신(v2)의 점심 항목을 다른 식당으로 교체
        ItineraryStop lunchStop = v2.getDays().get(0).getStops().get(1);
        when(stopRepository.findById(7L)).thenReturn(Optional.of(lunchStop));
        Restaurant next = restaurant(51, "새 점심집", "寿司");
        when(finder.find(anyDouble(), anyDouble(), any(Meal.class), anyInt(), any(FoodFilter.class), anySet(), anyInt()))
                .thenReturn(List.of(new RestaurantCandidate(next, 100, 3.9, false)));

        editService.replace(USER_ID, TRIP_ID, 7L, 51L);

        Itinerary saved = savedItinerary();
        assertThat(saved.getSource()).isEqualTo(ItinerarySource.EDITED);
        assertThat(saved.getChangeNote()).isEqualTo("DAY1 점심: 바꾼 점심집 → 새 점심집");
    }
}