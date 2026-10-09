package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.meal.MealBudget;
import com.example.japantravelplanner.itinerary.meal.MealProperties;
import com.example.japantravelplanner.itinerary.narration.ItineraryNarrator;
import com.example.japantravelplanner.itinerary.narration.TripBrief;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.planner.TravelOptions;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.travel.LegMode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ItineraryGenerationServiceTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPreferenceRepository preferenceRepository = mock(TripPreferenceRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final AttractionRepository attractionRepository = mock(AttractionRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final AccommodationLocator locator = mock(AccommodationLocator.class);
    private final ItineraryPlanner planner = mock(ItineraryPlanner.class);
    private final ItineraryNarrator narrator = mock(ItineraryNarrator.class);
    private final PlaceTypeLabels typeLabels = mock(PlaceTypeLabels.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();
    // 기본값(verifyRounds 0)이면 식당 확인을 하지 않는다 → 기존 테스트는 확인 없이 흐른다
    private final RestaurantMatchService matchService = mock(RestaurantMatchService.class);

    // 트랜잭션이 열려 있는 동안 true (AI 호출이 트랜잭션 밖인지 확인하는 용도)
    private final boolean[] transactionActive = {false};

    private final Trip trip = trip();
    private final Attraction cat = attraction(11, "新宿東口の猫", "tourist_attraction");
    private final Restaurant ramen = restaurant(21, "つけ麺 五ノ神製作所", "つけ麺");

    private ItineraryGenerationService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> {
            transactionActive[0] = true;
            return mock(TransactionStatus.class);
        });
        doAnswer(inv -> {
            transactionActive[0] = false;
            return null;
        }).when(transactionManager).commit(any());
        doAnswer(inv -> {
            transactionActive[0] = false;
            return null;
        }).when(transactionManager).rollback(any());

        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(locator.locate(trip)).thenReturn(GF.createPoint(new Coordinate(139.70, 35.69)));
        when(planner.plan(any(), any(), any())).thenReturn(plannedResult());
        when(attractionRepository.getReferenceById(11L)).thenReturn(cat);
        when(restaurantRepository.getReferenceById(21L)).thenReturn(ramen);
        when(typeLabels.toVisibleLabels(any())).thenReturn(List.of("관광명소"));
        when(narrator.narrate(any(), any(), any())).thenReturn(Map.of(1, "신주쿠의 하루예요."));
        when(itineraryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new ItineraryGenerationService(tripRepository, preferenceRepository, itineraryRepository,
                attractionRepository, restaurantRepository, locator, planner, narrator, typeLabels,
                new MealProperties(), lock, matchService, transactionManager);
    }

    private ItineraryPlanner.Result plannedResult() {
        ScheduledDay day = day(1, List.of(
                        attractionStop(cat, "16:15", "16:55", 40),
                        mealStop(StopType.DINNER, ramen, false, false, "18:00", "19:30", 24)),
                List.of(), List.of());
        return new ItineraryPlanner.Result(new TravelOptions(LegMode.TRANSIT, 20), true,
                new MealBudget(3100, 5700), List.of(day), List.of(window("16:00", "22:00")));
    }

    private void generate() {
        service.generate(USER_ID, TRIP_ID);
    }

    // ── 고른 식당 확인 (Google 매칭, 폐업 의심 식당 빼기) ──

    @Test
    void 고른_식당을_확인해서_빠진_곳이_있으면_다시_계산한다() {
        when(matchService.verifyRounds()).thenReturn(2);
        when(matchService.verify(any())).thenReturn(1, 0);

        generate();

        verify(planner, times(2)).plan(any(), any(), any());
        verify(matchService, times(2)).verify(Set.of(21L));   // 실제로 고른 식당만 확인한다
        verify(itineraryRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void 빠진_식당이_없으면_다시_계산하지_않는다() {
        when(matchService.verifyRounds()).thenReturn(2);
        when(matchService.verify(any())).thenReturn(0);

        generate();

        verify(planner, times(1)).plan(any(), any(), any());
        verify(matchService, times(1)).verify(Set.of(21L));
    }

    @Test
    void 다시_계산은_정해진_횟수까지만_하고_마지막_결과로_저장한다() {
        when(matchService.verifyRounds()).thenReturn(2);
        when(matchService.verify(any())).thenReturn(1);   // 계속 빠지는 곳이 나와도

        generate();

        verify(matchService, times(2)).verify(any());
        verify(planner, times(3)).plan(any(), any(), any());
        verify(itineraryRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void 식당_확인은_트랜잭션_밖에서_한다() {
        boolean[] calledInTransaction = {false};
        when(matchService.verifyRounds()).thenReturn(1);
        when(matchService.verify(any())).thenAnswer(inv -> {
            calledInTransaction[0] |= transactionActive[0];
            return 0;
        });

        generate();

        assertThat(calledInTransaction[0]).isFalse();
        verify(matchService).verify(any());
    }

    @Test
    void 일정에_들어간_식당_id만_모은다() {
        assertThat(ItineraryGenerationService.chosenRestaurantIds(plannedResult())).containsExactly(21L);
    }

    // ── 정상 흐름 ──

    @Test
    void 일정을_저장하고_여행_상태를_생성됨으로_바꾼다() {
        trip.setStatus(TripStatus.READY_TO_GENERATE);

        ItineraryResponseDto response = service.generate(USER_ID, TRIP_ID);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).description()).isEqualTo("신주쿠의 하루예요.");
        assertThat(response.days().get(0).stops()).hasSize(2);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.GENERATED);
        verify(itineraryRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void 저장되는_일정은_기본_환율을_함께_담는다() {
        ArgumentCaptor<Itinerary> saved = ArgumentCaptor.forClass(Itinerary.class);

        generate();

        verify(itineraryRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getKrwPerYen()).isEqualTo(new MealProperties().getKrwPerYen());
        assertThat(saved.getValue().getTrip()).isSameAs(trip);
    }

    @Test
    void 다시_만들면_버전이_올라가고_이전_버전은_그대로다() {
        trip.setStatus(TripStatus.GENERATED);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID))
                .thenReturn(Optional.of(Itinerary.create(trip, 2, 9.0)));

        ItineraryResponseDto response = service.generate(USER_ID, TRIP_ID);

        assertThat(response.version()).isEqualTo(3);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.GENERATED);
        verify(itineraryRepository, never()).delete(any());
    }

    @Test
    void 설명이_없어도_일정은_저장된다() {
        when(narrator.narrate(any(), any(), any())).thenReturn(Map.of());

        ItineraryResponseDto response = service.generate(USER_ID, TRIP_ID);

        assertThat(response.days().get(0).description()).isNull();
        assertThat(response.days().get(0).stops()).hasSize(2);
        verify(itineraryRepository).saveAndFlush(any());
    }

    @Test
    void 설명에는_여행_정보와_계산된_일정이_전달된다() {
        ArgumentCaptor<TripBrief> brief = ArgumentCaptor.forClass(TripBrief.class);

        generate();

        verify(narrator).narrate(brief.capture(), any(), any());
        assertThat(brief.getValue().travelers()).isEqualTo(2);
        assertThat(brief.getValue().interests()).hasSize(2);
    }

    // ── 트랜잭션 ──

    @Test
    void AI_설명은_트랜잭션_밖에서_부른다() {
        boolean[] activeDuringNarration = {true};
        when(narrator.narrate(any(), any(), any())).thenAnswer(inv -> {
            activeDuringNarration[0] = transactionActive[0];
            return Map.of();
        });

        generate();

        assertThat(activeDuringNarration[0]).isFalse();
        verify(transactionManager, times(2)).getTransaction(any());   // 계산 + 저장, 설명은 사이
    }

    // ── 거절 ──

    @Test
    void 없거나_남의_여행은_찾을_수_없다() {
        when(tripRepository.findByIdAndUserId(TRIP_ID, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(99L, TRIP_ID)).isInstanceOf(NotFoundException.class);

        verify(planner, never()).plan(any(), any(), any());
        verify(narrator, never()).narrate(any(), any(), any());
    }

    @Test
    void 하루_다섯_번을_넘기면_거절하고_AI도_부르지_않는다() {
        when(itineraryRepository.countByTripIdAndSourceAndCreatedAtAfter(
                eq(TRIP_ID), eq(ItinerarySource.GENERATED), any(LocalDateTime.class))).thenReturn(5L);   // 두 번째는 4L

        assertThatThrownBy(this::generate).isInstanceOf(TooManyRequestsException.class);

        verify(planner, never()).plan(any(), any(), any());
        verify(narrator, never()).narrate(any(), any(), any());
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 네_번까지는_만들_수_있다() {
        when(itineraryRepository.countByTripIdAndSourceAndCreatedAtAfter(
                eq(TRIP_ID), eq(ItinerarySource.GENERATED), any(LocalDateTime.class))).thenReturn(4L);   // 두 번째는 4L

        generate();

        verify(itineraryRepository).saveAndFlush(any());
    }

    // ── 동시 요청 ──

    @Test
    void 이미_만드는_중이면_거절하고_다른_요청의_잠금은_풀지_않는다() {
        lock.tryAcquire(TRIP_ID);   // 다른 요청이 만드는 중

        assertThatThrownBy(this::generate).isInstanceOf(ConflictException.class);

        verify(planner, never()).plan(any(), any(), any());
        verify(narrator, never()).narrate(any(), any(), any());
        assertThat(lock.tryAcquire(TRIP_ID)).isFalse();   // 여전히 잠겨 있다
    }

    @Test
    void 만들다_실패해도_잠금은_풀린다() {
        when(planner.plan(any(), any(), any())).thenThrow(new IllegalStateException("계산 실패"));

        assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    @Test
    void 성공해도_잠금은_풀린다() {
        generate();
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    @Test
    void 같은_버전으로_동시에_저장하면_충돌로_안내한다() {
        when(itineraryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_itineraries_trip_version\""));

        assertThatThrownBy(this::generate).isInstanceOf(ConflictException.class);
        assertThat(trip.getStatus()).isNotEqualTo(TripStatus.GENERATED);
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    @Test
    void 버전_번호_충돌이_아닌_DB_거절은_409로_바꾸지_않고_그대로_던진다() {
        // 교체·되돌리기와 같은 기준. 예: enum 허용 값 제약에 걸린 경우를 동시성 문제로 오해하게 만들면 안 된다
        when(itineraryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "violates check constraint \"itineraries_source_check\""));

        assertThatThrownBy(this::generate)
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ConflictException.class);
        assertThat(trip.getStatus()).isNotEqualTo(TripStatus.GENERATED);
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();   // 이 경우에도 잠금은 풀린다
    }

    // ── 조회 ──

    @Test
    void 가장_최근_버전을_돌려준다() {
        Itinerary latest = ItineraryMapper.toEntity(trip, 4, 9.0, plannedResult().days(), plannedResult().windows(),
                Map.of(), id -> cat, id -> ramen);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(latest));

        ItineraryResponseDto response = service.getLatest(USER_ID, TRIP_ID);

        assertThat(response.version()).isEqualTo(4);
        assertThat(response.days().get(0).stops()).hasSize(2);
    }

    @Test
    void 만들어진_일정이_없으면_찾을_수_없다() {
        assertThatThrownBy(() -> service.getLatest(USER_ID, TRIP_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 남의_여행_일정은_조회할_수_없다() {
        assertThatThrownBy(() -> service.getLatest(99L, TRIP_ID)).isInstanceOf(NotFoundException.class);
        verify(itineraryRepository, never()).findFirstByTripIdOrderByVersionNoDesc(anyLong());
    }
}