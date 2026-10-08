package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.meal.FoodFilterResolver;
import com.example.japantravelplanner.itinerary.meal.MealBudgetCalculator;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;
import com.example.japantravelplanner.itinerary.planner.DayScheduler;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.repository.ItineraryStopRepository;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 여행 조건(숙소·이동수단·이동 제약)이 바뀌었을 때: 장소는 그대로 두고 모든 날의 시각만 다시 계산한다.
 * 이동 시간은 진짜 계산(TravelTimeService·DayScheduler)으로 확인한다.
 */
class ItineraryConditionRetimeTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;
    private static final String NOTE = "여행 조건 변경(이동수단): 장소는 그대로, 시각 다시 계산";

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final AccommodationLocator locator = mock(AccommodationLocator.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();

    private final Trip trip = trip();
    // 관광지와 숙소는 같은 자리, 식당은 약 1.4km 떨어져 있다 (걸어서 20분이 넘어 탈것으로 이동)
    private final Attraction a1 = attraction(11, "관광지1", "tourist_attraction");
    private final Attraction a2 = attraction(12, "관광지2", "tourist_attraction");
    private final Restaurant lunch = restaurant(21, "점심집", "ラーメン");
    private final Restaurant dinner = restaurant(22, "저녁집", "洋食");

    private ItineraryEditService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(itineraryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(locator.locate(trip)).thenReturn(GF.createPoint(new Coordinate(139.70, 35.69)));

        PlaceTypeLabels labels = mock(PlaceTypeLabels.class);
        when(labels.toVisibleLabels(any())).thenReturn(List.of("관광명소"));
        TravelTimeService travel = new TravelTimeService(new TravelProperties(), null);
        service = new ItineraryEditService(tripRepository, mock(TripPreferenceRepository.class), itineraryRepository,
                mock(ItineraryStopRepository.class), mock(RestaurantRepository.class), locator,
                mock(RestaurantFinder.class), mock(MealBudgetCalculator.class), mock(FoodFilterResolver.class),
                travel, new TravelProperties(), new DayScheduler(travel), labels, lock, transactionManager);
    }

    /** 관광지1 → 점심 → 관광지2 → 저녁 하루. 사용자가 고른 정기 휴일 식당 안내가 남아 있다 */
    private Itinerary latest(int versionNo) {
        ScheduledDay day = day(1, List.of(
                        attractionStop(a1, "10:00", "10:40", 40),
                        mealStopWithAlternatives(StopType.LUNCH, lunch, List.of(), "12:00", "13:00"),
                        attractionStop(a2, "13:30", "14:10", 40),
                        mealStopWithAlternatives(StopType.DINNER, dinner, List.of(), "18:00", "19:30")),
                List.of(), List.of("점심집은(는) 이 날이 정기 휴일이에요. 영업하는지 확인해주세요."));
        Map<Long, Attraction> attractions = Map.of(11L, a1, 12L, a2);
        Map<Long, Restaurant> restaurants = Map.of(21L, lunch, 22L, dinner);
        Itinerary itinerary = ItineraryMapper.toEntity(trip, versionNo, 9.0, List.of(day),
                List.of(window("09:30", "22:00")), Map.of(1, "하루 설명"), attractions::get, restaurants::get);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(itinerary));
        return itinerary;
    }

    private Itinerary savedItinerary() {
        ArgumentCaptor<Itinerary> captor = ArgumentCaptor.forClass(Itinerary.class);
        verify(itineraryRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void 이동수단을_렌트카로_바꾸면_장소는_그대로_두고_시각만_다시_계산해_EDITED로_저장한다() {
        latest(3);
        trip.setTransportMode(TransportMode.RENTAL_CAR);

        Optional<ItineraryResponseDto> response = service.retimeForConditions(USER_ID, TRIP_ID, NOTE);

        Itinerary saved = savedItinerary();
        assertThat(saved.getVersionNo()).isEqualTo(4);
        assertThat(saved.getSource()).isEqualTo(ItinerarySource.EDITED);
        assertThat(saved.getChangeNote()).isEqualTo(NOTE);

        List<ItineraryStop> stops = saved.getDays().get(0).getStops();
        assertThat(stops).extracting(ItineraryStop::getType)
                .containsExactly(StopType.ATTRACTION, StopType.LUNCH, StopType.ATTRACTION, StopType.DINNER);
        assertThat(stops.get(0).getAttraction()).isSameAs(a1);
        assertThat(stops.get(1).getRestaurant()).isSameAs(lunch);
        // 관광지1 → 점심집(약 1.4km)은 걸어서 20분이 넘으므로 렌트카로 간다
        assertThat(stops.get(1).getTravelMode()).isEqualTo(LegMode.DRIVE);
        assertThat(stops.get(0).getArrivalTime()).isNotEqualTo(java.time.LocalTime.of(10, 0));   // 시각을 다시 계산했다

        assertThat(trip.getStatus()).isEqualTo(TripStatus.EDITING);
        assertThat(response).isPresent();
        assertThat(response.get().version()).isEqualTo(4);
    }

    @Test
    void 장소가_그대로라_AI_설명과_정기_휴일_안내는_남긴다() {
        latest(1);
        trip.setTransportMode(TransportMode.RENTAL_CAR);

        service.retimeForConditions(USER_ID, TRIP_ID, NOTE);

        ItineraryDay day = savedItinerary().getDays().get(0);
        assertThat(day.getDescription()).isEqualTo("하루 설명");
        assertThat(day.getWarnings()).anyMatch(w -> w.contains("정기 휴일"));
    }

    @Test
    void 숙소_좌표를_다시_찾아서_첫_이동을_계산한다() {
        latest(1);

        service.retimeForConditions(USER_ID, TRIP_ID, NOTE);

        verify(locator).locate(trip);
    }

    @Test
    void 아직_만든_일정이_없으면_아무것도_저장하지_않는다() {
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.empty());

        assertThat(service.retimeForConditions(USER_ID, TRIP_ID, NOTE)).isEmpty();
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 다른_요청이_일정을_고치는_중이면_409() {
        latest(1);
        lock.tryAcquire(TRIP_ID);

        assertThatThrownBy(() -> service.retimeForConditions(USER_ID, TRIP_ID, NOTE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void 남의_여행은_고칠_수_없다() {
        latest(1);

        assertThatThrownBy(() -> service.retimeForConditions(999L, TRIP_ID, NOTE))
                .isInstanceOf(NotFoundException.class);
    }
}
