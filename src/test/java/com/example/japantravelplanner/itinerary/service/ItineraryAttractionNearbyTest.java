package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto.CandidateDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DayScheduler;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.repository.ItineraryStopRepository;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.mockito.ArgumentCaptor;
import com.example.japantravelplanner.itinerary.reason.ReasonContextLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 같은 지역 관광지가 다 쓰였을 때 3km 이내 다른 지역 후보로 넓히는 동작.
 * 롯폰기 하루: 힐즈(1) → 돌계단(2) → 점심 → 사찰(3) → 저녁 → 도쿄타워(4, 야경)
 */
class ItineraryAttractionNearbyTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;
    private static final long STAIRS_STOP_ID = 2L;   // 낮 관광지 (stops[1])
    private static final long TOWER_STOP_ID = 6L;    // 야경 (stops[5])

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final ItineraryStopRepository stopRepository = mock(ItineraryStopRepository.class);
    private final AccommodationLocator locator = mock(AccommodationLocator.class);
    private final AttractionScorer scorer = mock(AttractionScorer.class);
    private final PlaceTypeLabels typeLabels = mock(PlaceTypeLabels.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Trip trip = trip();

    // 일정에 이미 있는 곳 (롯폰기 지역)
    private final Attraction hills = spot(1, "롯폰기 힐즈", "observation_deck", 35.6600712, 139.7292907);
    private final Attraction stairs = spot(2, "출세의 돌계단", "tourist_attraction", 35.6646681, 139.7490137);
    private final Attraction temple = spot(3, "사찰", "place_of_worship", 35.657479, 139.7482928);
    private final Attraction tower = spot(4, "도쿄타워", "observation_deck", 35.6585805, 139.7454329);
    private final Restaurant lunch = restaurant(21, "점심집", "ラーメン");
    private final Restaurant dinner = restaurant(22, "저녁집", "洋食");

    // 다른 지역 후보 (돌계단에서 3km 안/밖)
    private final Attraction nearGarden = spot(41, "가까운 정원", "park", 35.6700, 139.7600);
    private final Attraction nearDeck = spot(42, "가까운 전망대", "observation_deck", 35.6660, 139.7550);
    private final Attraction nearMuseum = spot(43, "가까운 박물관", "museum", 35.6720, 139.7530);
    private final Attraction farPark = spot(44, "먼 공원", "park", 35.7140, 139.7860);
    // 같은 지역 여분
    private final Attraction spare1 = spot(51, "여분 공원 1", "park", 35.6620, 139.7330);
    private final Attraction spare2 = spot(52, "여분 공원 2", "park", 35.6630, 139.7340);
    private final Attraction spare3 = spot(53, "여분 공원 3", "park", 35.6640, 139.7350);

    private Itinerary latest;
    private ItineraryAttractionEditService service;

    private static Attraction spot(long id, String name, String types, double lat, double lng) {
        return Attraction.builder().id(id).name(name).types(types).rating(4.5).reviewCnt(1000).address("도쿄")
                .ward("港区").placeId("place-" + id).location(GF.createPoint(new Coordinate(lng, lat))).build();
    }

    private ScoredAttraction scored(Attraction a, TripZone zone, double score) {
        return new ScoredAttraction(a, zone, score);
    }

    @BeforeEach
    void setUp() throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(locator.locate(trip)).thenReturn(GF.createPoint(new Coordinate(139.7020, 35.6951)));
        when(typeLabels.toVisibleLabels(any())).thenReturn(List.of("관광명소"));
        when(itineraryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        latest = itinerary();
        List<ItineraryStop> stops = latest.getDays().get(0).getStops();
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(latest));
        when(stopRepository.findById(STAIRS_STOP_ID)).thenReturn(Optional.of(stops.get(1)));
        when(stopRepository.findById(TOWER_STOP_ID)).thenReturn(Optional.of(stops.get(5)));

        TravelTimeService travel = new TravelTimeService(new TravelProperties(), null);
        service = new ItineraryAttractionEditService(tripRepository, mock(TripPreferenceRepository.class),
                itineraryRepository, stopRepository, mock(AttractionRepository.class),
                mock(RestaurantRepository.class), locator, scorer, travel, new TravelProperties(),
                new DayScheduler(travel), typeLabels, new GenerationLock(), mock(ReasonContextLoader.class), transactionManager);
    }

    private Itinerary itinerary() throws Exception {
        ScheduledDay day1 = new ScheduledDay(1, LocalDate.of(2026, 10, 21), TripZone.ROPPONGI_TOWER, List.of(
                attractionStop(hills, "10:07", "11:07", 60),
                attractionStop(stairs, "11:33", "12:23", 50),
                mealStopWithAlternatives(StopType.LUNCH, lunch, List.of(), "12:43", "13:43"),
                attractionStop(temple, "16:56", "17:46", 50),
                mealStopWithAlternatives(StopType.DINNER, dinner, List.of(), "18:04", "19:34"),
                attractionStop(tower, "19:51", "20:51", 60)), List.of(), List.of());
        Map<Long, Attraction> attractions = Map.of(1L, hills, 2L, stairs, 3L, temple, 4L, tower);
        Map<Long, Restaurant> restaurants = Map.of(21L, lunch, 22L, dinner);
        Itinerary itinerary = ItineraryMapper.toEntity(trip, 1, 9.0, List.of(day1),
                List.of(window("09:30", "22:00")), Map.of(1, "하루1 설명"), attractions::get, restaurants::get);
        Field field = itinerary.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(itinerary, 100L);
        return itinerary;
    }

    /** 이미 쓰인 4곳(롯폰기)은 항상 점수 목록에 있고, 거기에 후보를 더한다 */
    private void pool(ScoredAttraction... extra) {
        List<ScoredAttraction> all = new ArrayList<>(List.of(
                scored(hills, TripZone.ROPPONGI_TOWER, 4.8),
                scored(stairs, TripZone.ROPPONGI_TOWER, 4.7),
                scored(temple, TripZone.ROPPONGI_TOWER, 4.6),
                scored(tower, TripZone.ROPPONGI_TOWER, 4.5)));
        all.addAll(List.of(extra));
        when(scorer.score(any(), any())).thenReturn(all);
    }

    private static List<Long> ids(AttractionCandidatesResponseDto response) {
        return response.candidates().stream().map(CandidateDto::id).toList();
    }

    @Test
    void 같은_지역이_다_쓰이면_3km_안의_다른_지역으로_넓힌다() {
        pool(scored(nearGarden, TripZone.GINZA_TOKYO, 4.2),
                scored(nearMuseum, TripZone.GINZA_TOKYO, 4.4),
                scored(farPark, TripZone.ASAKUSA_UENO, 4.9));

        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, STAIRS_STOP_ID);

        assertThat(ids(response)).containsExactly(43L, 41L);   // 점수순, 먼 공원(44)은 3km 밖이라 제외
        assertThat(response.broadened()).isTrue();
        assertThat(response.candidates()).allMatch(c -> !c.sameZone());
    }

    @Test
    void 같은_지역_후보가_3개_이상이면_넓히지_않는다() {
        pool(scored(spare1, TripZone.ROPPONGI_TOWER, 4.0),
                scored(spare2, TripZone.ROPPONGI_TOWER, 3.9),
                scored(spare3, TripZone.ROPPONGI_TOWER, 3.8),
                scored(nearGarden, TripZone.GINZA_TOKYO, 4.9));

        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, STAIRS_STOP_ID);

        assertThat(ids(response)).containsExactly(51L, 52L, 53L);
        assertThat(response.broadened()).isFalse();
    }

    @Test
    void 같은_지역_후보가_모자라면_같은_지역이_먼저_그_뒤에_다른_지역이_온다() {
        pool(scored(spare1, TripZone.ROPPONGI_TOWER, 3.0),
                scored(nearGarden, TripZone.GINZA_TOKYO, 4.9),
                scored(nearMuseum, TripZone.GINZA_TOKYO, 4.8));

        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, STAIRS_STOP_ID);

        assertThat(ids(response)).containsExactly(51L, 41L, 43L);
        assertThat(response.candidates().stream().map(CandidateDto::sameZone).toList())
                .containsExactly(true, false, false);
        assertThat(response.broadened()).isTrue();
    }

    @Test
    void 삼킬로_안에_아무것도_없으면_빈_목록이고_broadened는_false다() {
        pool(scored(farPark, TripZone.ASAKUSA_UENO, 4.9));

        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, STAIRS_STOP_ID);

        assertThat(response.candidates()).isEmpty();
        assertThat(response.broadened()).isFalse();
    }

    @Test
    void 이미_쓴_장소는_다른_지역으로_분류돼도_후보에서_빠진다() {
        when(scorer.score(any(), any())).thenReturn(List.of(
                scored(hills, TripZone.GINZA_TOKYO, 4.8),
                scored(stairs, TripZone.ROPPONGI_TOWER, 4.7),
                scored(temple, TripZone.ROPPONGI_TOWER, 4.6),
                scored(tower, TripZone.ROPPONGI_TOWER, 4.5),
                scored(nearGarden, TripZone.GINZA_TOKYO, 4.2)));

        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, STAIRS_STOP_ID);

        assertThat(ids(response)).containsExactly(41L);
    }

    @Test
    void 넓힌_후보로_교체하면_새_버전에_저장되고_변경_설명이_남는다() {
        pool(scored(nearGarden, TripZone.GINZA_TOKYO, 4.2));

        service.replace(USER_ID, TRIP_ID, STAIRS_STOP_ID, 41L);

        ArgumentCaptor<Itinerary> captor = ArgumentCaptor.forClass(Itinerary.class);
        verify(itineraryRepository).saveAndFlush(captor.capture());
        Itinerary saved = captor.getValue();
        assertThat(saved.getVersionNo()).isEqualTo(2);
        assertThat(saved.getChangeNote()).isEqualTo("DAY1 관광지: 출세의 돌계단 → 가까운 정원");
        ItineraryStop changed = saved.getDays().get(0).getStops().get(1);
        assertThat(changed.getAttraction().getId()).isEqualTo(41L);
        assertThat(changed.getStayMinutes()).isEqualTo(75);   // 공원
    }

    @Test
    void 삼킬로_밖의_다른_지역_장소로는_교체할_수_없다() {
        pool(scored(farPark, TripZone.ASAKUSA_UENO, 4.9));

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, STAIRS_STOP_ID, 44L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("넣을 수 없는");
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 야경_장소는_전망대만_넓은_후보가_된다() {
        pool(scored(nearDeck, TripZone.GINZA_TOKYO, 4.1),
                scored(nearGarden, TripZone.GINZA_TOKYO, 4.9),
                scored(nearMuseum, TripZone.GINZA_TOKYO, 4.8));

        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, TOWER_STOP_ID);

        assertThat(response.night()).isTrue();
        assertThat(ids(response)).containsExactly(42L);
        assertThat(response.broadened()).isTrue();
    }
}
