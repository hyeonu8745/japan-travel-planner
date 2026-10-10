package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.StopCandidatesResponseDto;
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
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
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
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Field;
import java.time.LocalTime;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 식당 교체와 후보 조회 (저장소와 후보 검색은 가짜, 시각 계산은 진짜) */
class ItineraryEditServiceTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPreferenceRepository preferenceRepository = mock(TripPreferenceRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final ItineraryStopRepository stopRepository = mock(ItineraryStopRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final AccommodationLocator locator = mock(AccommodationLocator.class);
    private final RestaurantFinder finder = mock(RestaurantFinder.class);
    private final MealBudgetCalculator budgetCalculator = mock(MealBudgetCalculator.class);
    private final FoodFilterResolver filterResolver = mock(FoodFilterResolver.class);
    private final PlaceTypeLabels typeLabels = mock(PlaceTypeLabels.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();
    private final RestaurantMatchService matchService = mock(RestaurantMatchService.class);

    private final Trip trip = trip();
    private final Attraction a1 = attraction(11, "관광지1", "tourist_attraction");
    private final Attraction a2 = attraction(12, "관광지2", "tourist_attraction");
    private final Attraction a3 = attraction(13, "관광지3", "tourist_attraction");

    private final Restaurant lunch = restaurant(21, "점심집(라멘)", "ラーメン");
    private final Restaurant dinner1 = restaurant(22, "저녁집1(양식)", "洋食");
    private final Restaurant dinner2 = restaurant(23, "저녁집2", "寿司");
    private final Restaurant alt1 = restaurant(31, "대안1", "うどん");
    private final Restaurant alt2 = restaurant(32, "대안2", "そば");

    private Itinerary latest;
    private ItineraryStop lunchStop;
    private ItineraryStop dinnerStop;
    private ItineraryStop attractionStop;
    private ItineraryEditService service;

    @BeforeEach
    void setUp() throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(locator.locate(trip)).thenReturn(GF.createPoint(new Coordinate(139.70, 35.69)));
        when(budgetCalculator.calculate(anyInt(), anyInt(), anyInt(), anyDouble())).thenReturn(new MealBudget(3100, 5700));
        when(filterResolver.resolve(any(), any())).thenReturn(new FoodFilter(Set.of(), Set.of(), false));
        when(typeLabels.toVisibleLabels(any())).thenReturn(List.of("관광명소"));
        when(itineraryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(finder.find(anyDouble(), anyDouble(), any(Meal.class), anyInt(), any(FoodFilter.class), anySet(), anyInt()))
                .thenReturn(List.of());

        latest = itinerary(trip);
        setId(latest, 100L);
        lunchStop = latest.getDays().get(0).getStops().get(1);
        attractionStop = latest.getDays().get(0).getStops().get(0);
        dinnerStop = latest.getDays().get(0).getStops().get(3);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(latest));
        when(stopRepository.findById(1L)).thenReturn(Optional.of(lunchStop));
        when(stopRepository.findById(2L)).thenReturn(Optional.of(attractionStop));
        when(stopRepository.findById(3L)).thenReturn(Optional.of(dinnerStop));

        service = new ItineraryEditService(tripRepository, preferenceRepository, itineraryRepository, stopRepository,
                restaurantRepository, locator, finder, budgetCalculator, filterResolver,
                new TravelTimeService(new TravelProperties(), null), new TravelProperties(),
                new DayScheduler(new TravelTimeService(new TravelProperties(), null)),
                typeLabels, lock, matchService, transactionManager);
    }

    // ── 테스트 자료 ──

    /** DAY1: 관광지1 → 점심(라멘, 대안 2곳) → 관광지2 → 저녁(양식) / DAY2: 관광지3 → 저녁(초밥) */
    private Itinerary itinerary(Trip trip) {
        ScheduledDay day1 = day(1, List.of(
                        attractionStop(a1, "10:00", "10:40", 40),
                        mealStopWithAlternatives(StopType.LUNCH, lunch, List.of(alt1, alt2), "12:00", "13:00"),
                        attractionStop(a2, "13:30", "14:10", 40),
                        mealStopWithAlternatives(StopType.DINNER, dinner1, List.of(), "18:00", "19:30")),
                List.of(), List.of("안내 문장"));
        ScheduledDay day2 = day(2, List.of(
                        attractionStop(a3, "10:00", "10:40", 40),
                        mealStopWithAlternatives(StopType.DINNER, dinner2, List.of(), "18:00", "19:30")),
                List.of(), List.of());
        Map<Long, Attraction> attractions = Map.of(11L, a1, 12L, a2, 13L, a3);
        Map<Long, Restaurant> restaurants = Map.of(21L, lunch, 22L, dinner1, 23L, dinner2);
        return ItineraryMapper.toEntity(trip, 1, 9.0, List.of(day1, day2),
                List.of(window("09:30", "22:00"), window("09:30", "22:00")),
                Map.of(1, "하루1 설명", 2, "하루2 설명"), attractions::get, restaurants::get);
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static RestaurantCandidate candidate(Restaurant r, boolean budgetRelaxed) {
        return new RestaurantCandidate(r, 150, 3.9, budgetRelaxed);
    }

    private void finderReturns(RestaurantCandidate... candidates) {
        when(finder.find(anyDouble(), anyDouble(), any(Meal.class), anyInt(), any(FoodFilter.class), anySet(), anyInt()))
                .thenReturn(List.of(candidates));
    }

    private Itinerary savedItinerary() {
        ArgumentCaptor<Itinerary> captor = ArgumentCaptor.forClass(Itinerary.class);
        verify(itineraryRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ── 후보 조회 ──

    @Test
    void 후보는_이동_시간과_함께_나가고_예산_상한을_알려준다() {
        Restaurant near = restaurant(41, "가까운 후보", "うどん");
        finderReturns(candidate(near, false));

        StopCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, 1L);

        assertThat(response.type()).isEqualTo(StopType.LUNCH);
        assertThat(response.budgetMaxYen()).isEqualTo(3100);
        assertThat(response.candidates()).hasSize(1);
        StopCandidatesResponseDto.CandidateDto c = response.candidates().get(0);
        assertThat(c.name()).isEqualTo("가까운 후보");
        assertThat(c.budget()).isEqualTo("～￥999");   // 점심이라 점심 예산
        assertThat(c.lat()).isEqualTo(35.70);
        assertThat(c.lng()).isEqualTo(139.71);
        assertThat(c.travel().mode()).isNotNull();
        assertThat(c.travel().minutes()).isPositive();
    }

    @Test
    void 후보는_앞뒤_장소의_중간에서_찾고_이미_쓴_식당은_제외한다() {
        finderReturns();

        service.candidates(USER_ID, TRIP_ID, 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> excluded = ArgumentCaptor.forClass(Set.class);
        verify(finder).find(eq(35.69), eq(139.70), eq(Meal.LUNCH), eq(3100), any(FoodFilter.class),
                excluded.capture(), eq(ItineraryEditService.CANDIDATE_LIMIT));
        assertThat(excluded.getValue()).containsExactlyInAnyOrder(21L, 22L, 23L);
    }

    @Test
    void 저녁_후보는_점심과_같은_음식이면_표시한다() {
        Restaurant sameGenre = restaurant(42, "라멘 후보", "ラーメン,つけ麺");
        Restaurant otherGenre = restaurant(43, "초밥 후보", "寿司");
        finderReturns(candidate(sameGenre, false), candidate(otherGenre, false));

        StopCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, 3L);

        // 저녁 바로 앞 끼니는 점심(라멘)
        assertThat(response.budgetMaxYen()).isEqualTo(5700);
        assertThat(response.candidates().get(0).genreRepeated()).isTrue();
        assertThat(response.candidates().get(1).genreRepeated()).isFalse();
    }

    @Test
    void 관광지_항목은_바꿀_수_없다() {
        assertThatThrownBy(() -> service.candidates(USER_ID, TRIP_ID, 2L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("식당");
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 2L, 41L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 남의_여행이거나_없는_항목은_찾을_수_없다() {
        assertThatThrownBy(() -> service.candidates(999L, TRIP_ID, 1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.candidates(USER_ID, TRIP_ID, 404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 다른_여행의_항목은_찾을_수_없다() {
        Trip other = Trip.builder().id(99L).startDate(trip.getStartDate()).endDate(trip.getEndDate()).build();
        ItineraryStop otherStop = itinerary(other).getDays().get(0).getStops().get(1);
        when(stopRepository.findById(77L)).thenReturn(Optional.of(otherStop));

        assertThatThrownBy(() -> service.candidates(USER_ID, TRIP_ID, 77L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 이미_새_버전이_생긴_옛_항목은_충돌이다() throws Exception {
        Itinerary old = itinerary(trip);
        setId(old, 99L);   // 최신(100)이 아니다
        ItineraryStop oldStop = old.getDays().get(0).getStops().get(1);
        when(stopRepository.findById(88L)).thenReturn(Optional.of(oldStop));

        assertThatThrownBy(() -> service.candidates(USER_ID, TRIP_ID, 88L))
                .isInstanceOf(ConflictException.class).hasMessageContaining("새로고침");
    }

    // ── 교체 ──

    // ── 폐업 의심 식당 (Google 매칭) ──

    @Test
    void 교체_전에_고른_식당을_트랜잭션_밖에서_Google로_확인한다() {
        finderReturns(candidate(restaurant(41, "새 점심집", "洋食"), false));

        service.replace(USER_ID, TRIP_ID, 1L, 41L);

        InOrder order = inOrder(matchService, transactionManager);
        order.verify(matchService).verify(Set.of(41L));
        order.verify(transactionManager, atLeastOnce()).getTransaction(any());
    }

    @Test
    void 확인해보니_찾지_못했거나_폐업이면_교체를_막는다() {
        Restaurant closed = restaurant(41, "폐업 의심 집", "洋食");
        finderReturns(candidate(closed, false));
        when(restaurantRepository.findById(41L)).thenReturn(Optional.of(closed));
        doAnswer(inv -> {
            closed.recordGoogleMatch(GoogleMatchStatus.NOT_FOUND, null, java.time.LocalDateTime.now());
            return 1;
        }).when(matchService).verify(Set.of(41L));

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 41L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("폐업했을 수 있어요");
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 저장된_대안이라도_폐업으로_확인된_식당이면_막는다() {
        alt1.recordGoogleMatch(GoogleMatchStatus.CLOSED, "p-closed", java.time.LocalDateTime.now());
        when(restaurantRepository.findById(31L)).thenReturn(Optional.of(alt1));

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 31L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("폐업했을 수 있어요");
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 교체하면_새_버전_EDITED로_저장되고_이전_버전은_그대로다() {
        Restaurant replacement = restaurant(41, "새 점심집", "洋食");
        finderReturns(candidate(replacement, true));

        ItineraryResponseDto response = service.replace(USER_ID, TRIP_ID, 1L, 41L);

        Itinerary saved = savedItinerary();
        assertThat(saved.getVersionNo()).isEqualTo(2);
        assertThat(saved.getSource()).isEqualTo(ItinerarySource.EDITED);
        assertThat(saved.getKrwPerYen()).isEqualTo(9.0);
        assertThat(saved.getDays()).hasSize(2);

        ItineraryStop newLunch = saved.getDays().get(0).getStops().get(1);
        assertThat(newLunch.getRestaurant().getId()).isEqualTo(41L);
        assertThat(newLunch.isBudgetRelaxed()).isTrue();   // 후보의 "예산을 올려서 찾음" 표시가 따라온다

        // 이전 버전은 그대로
        assertThat(lunchStop.getRestaurant().getId()).isEqualTo(21L);
        assertThat(latest.getVersionNo()).isEqualTo(1);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.source()).isEqualTo(ItinerarySource.EDITED);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.EDITING);
    }

    @Test
    void 바뀐_날은_설명을_비우고_다른_날은_그대로_복사한다() {
        finderReturns(candidate(restaurant(41, "새 점심집", "洋食"), false));

        service.replace(USER_ID, TRIP_ID, 1L, 41L);

        Itinerary saved = savedItinerary();
        assertThat(saved.getDays().get(0).getDescription()).isNull();           // 식당 이름이 달라져서
        assertThat(saved.getDays().get(1).getDescription()).isEqualTo("하루2 설명");
        assertThat(saved.getDays().get(1).getStops()).hasSize(2);
        assertThat(saved.getDays().get(1).getStops().get(1).getRestaurant().getId()).isEqualTo(23L);
        assertThat(saved.getDays().get(1).getStops().get(0).getArrivalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void 바뀐_날의_순서와_개수는_그대로이고_시각이_다시_계산된다() {
        Restaurant far = Restaurant.builder().id(41L).name("먼 점심집").genre("洋食").ratingVal(3.9)
                .budgetLunch("～￥999").budgetDinner("～￥999").address("롯폰기")
                .location(GF.createPoint(new Coordinate(139.7292, 35.6600))).build();
        finderReturns(candidate(far, false));

        service.replace(USER_ID, TRIP_ID, 1L, 41L);

        List<ItineraryStop> stops = savedItinerary().getDays().get(0).getStops();
        assertThat(stops).hasSize(4);
        assertThat(stops).extracting(ItineraryStop::getType)
                .containsExactly(StopType.ATTRACTION, StopType.LUNCH, StopType.ATTRACTION, StopType.DINNER);
        // 점심 식당까지의 이동이 처음(10분)과 달라진다
        assertThat(stops.get(1).getTravelMinutes()).isNotEqualTo(10);
        // 시각은 순서대로 이어진다
        for (int i = 1; i < stops.size(); i++) {
            assertThat(stops.get(i).getArrivalTime()).isAfter(stops.get(i - 1).getDepartureTime());
        }
        // 체류 시간은 그대로
        assertThat(stops.get(1).getStayMinutes()).isEqualTo(60);
    }

    @Test
    void 식당을_바꾸면_이전_식당이_맨_앞_대안이_되고_새로_고른_식당은_대안에서_빠진다() {
        // 점심 항목의 대안은 [31, 32]. 대안이던 31을 고르면 [21(이전 식당), 32]
        when(restaurantRepository.findById(31L)).thenReturn(Optional.of(alt1));
        finderReturns(candidate(alt1, false));
        // 점심 항목에 대안이 저장되어 있다는 가정 (테스트 자료 mealStopWithAlternatives)
        assertThat(lunchStop.getAlternativeRestaurantIds()).containsExactly(31L, 32L);

        service.replace(USER_ID, TRIP_ID, 1L, 31L);

        ItineraryStop newLunch = savedItinerary().getDays().get(0).getStops().get(1);
        assertThat(newLunch.getAlternativeRestaurantIds()).containsExactly(21L, 32L);
    }

    @Test
    void 바뀐_식당_때문에_다음_끼니가_같은_음식이_되면_표시도_바뀐다() {
        // 점심(라멘)이 양식으로 바뀌면 저녁(양식)이 앞 끼니와 같은 음식이 된다
        finderReturns(candidate(restaurant(41, "양식 점심집", "洋食"), false));
        assertThat(dinnerStop.isGenreRepeated()).isFalse();

        service.replace(USER_ID, TRIP_ID, 1L, 41L);

        List<ItineraryStop> stops = savedItinerary().getDays().get(0).getStops();
        assertThat(stops.get(1).isGenreRepeated()).isFalse();   // 점심 앞에는 끼니가 없다
        assertThat(stops.get(3).isGenreRepeated()).isTrue();    // 저녁이 점심과 같은 양식
    }

    @Test
    void 일정을_만들_때_저장해둔_대안은_후보_검색에_없어도_고를_수_있다() {
        finderReturns();   // 이번 검색에는 아무것도 안 나온다
        when(restaurantRepository.findById(31L)).thenReturn(Optional.of(alt1));

        service.replace(USER_ID, TRIP_ID, 1L, 31L);

        assertThat(savedItinerary().getDays().get(0).getStops().get(1).getRestaurant().getId()).isEqualTo(31L);
    }

    @Test
    void 후보에도_대안에도_없는_식당은_바꿀_수_없다() {
        finderReturns(candidate(restaurant(41, "새 점심집", "洋食"), false));

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 999L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("넣을 수 없는");
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 지금_정해진_식당과_같으면_바꿀_수_없다() {
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 21L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이미");
    }

    // ── 동시성과 실패 ──

    @Test
    void 저장_충돌은_409로_안내하고_잠금은_풀린다() {
        finderReturns(candidate(restaurant(41, "새 점심집", "洋食"), false));
        when(itineraryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_itineraries_trip_version"));

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 41L)).isInstanceOf(ConflictException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    @Test
    void 같은_여행을_동시에_고치거나_만들_수_없다() {
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();   // 누군가 이미 작업 중

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 41L))
                .isInstanceOf(ConflictException.class);
        // 거절당한 요청이 다른 요청의 잠금을 풀어버리지 않는다
        assertThat(lock.tryAcquire(TRIP_ID)).isFalse();
    }

    @Test
    void 실패한_요청도_잠금을_풀어준다() {
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 2L, 41L))   // 관광지라 실패
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    // ── 정기 휴일 (DAY1은 2026-10-20 화요일) ──

    private static Restaurant withHoliday(long id, String name, String holiday) {
        return Restaurant.builder().id(id).name(name).genre("洋食").ratingVal(3.8)
                .budgetLunch("～￥999").budgetDinner("￥3,000～￥3,999").address("도쿄 식당 주소").holiday(holiday)
                .location(GF.createPoint(new Coordinate(139.71, 35.70))).build();
    }

    @Test
    void 후보에는_정기_휴일_식당도_나오되_휴일_표시가_붙는다() {
        Restaurant closed = withHoliday(41, "화요일 휴무", "火曜日");
        Restaurant open = withHoliday(42, "일요일 휴무", "日曜日");
        Restaurant unsure = withHoliday(43, "비정기 휴무", "不定休");
        finderReturns(candidate(closed, false), candidate(open, false), candidate(unsure, false));

        StopCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, 1L);

        // 후보에서 빼지 않고 표시만 한다 (사용자가 알고 고를 수 있게)
        assertThat(response.candidates()).hasSize(3);
        assertThat(response.candidates().get(0).closedOnVisitDay()).isTrue();
        assertThat(response.candidates().get(0).holiday()).isEqualTo("火曜日");
        assertThat(response.candidates().get(1).closedOnVisitDay()).isFalse();
        assertThat(response.candidates().get(2).closedOnVisitDay()).isFalse();   // 애매한 문구는 휴일로 보지 않는다
    }

    @Test
    void 정기_휴일인_식당으로_바꾸면_막지_않고_안내_문장을_남긴다() {
        finderReturns(candidate(withHoliday(41, "화요일 휴무 식당", "火曜日"), false));

        ItineraryResponseDto response = service.replace(USER_ID, TRIP_ID, 1L, 41L);

        List<String> warnings = savedItinerary().getDays().get(0).getWarnings();
        assertThat(warnings).anyMatch(w -> w.contains("화요일 휴무 식당") && w.contains("정기 휴일"));
        assertThat(warnings).contains("안내 문장");   // 원래 있던 다른 안내는 그대로
        assertThat(response.days().get(0).stops().get(1).restaurant().closedOnVisitDay()).isTrue();
    }

    @Test
    void 휴일이_아닌_식당으로_바꾸면_휴일_안내가_없다() {
        finderReturns(candidate(withHoliday(41, "일요일 휴무 식당", "日曜日"), false));

        service.replace(USER_ID, TRIP_ID, 1L, 41L);

        assertThat(savedItinerary().getDays().get(0).getWarnings()).noneMatch(w -> w.contains("정기 휴일"));
    }

    @Test
    void 이전의_휴일_안내는_새로_계산한다() {
        // 이전 식당 때문에 남은 휴일 안내가 있었다면, 휴일이 아닌 식당으로 바꾼 뒤에는 사라져야 한다
        latest.getDays().get(0).getWarnings().add("옛 식당은 이 날이 정기 휴일이에요. 영업하는지 확인해주세요.");
        finderReturns(candidate(withHoliday(41, "일요일 휴무 식당", "日曜日"), false));

        service.replace(USER_ID, TRIP_ID, 1L, 41L);

        assertThat(savedItinerary().getDays().get(0).getWarnings()).noneMatch(w -> w.contains("정기 휴일"));
    }

}