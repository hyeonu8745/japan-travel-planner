package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto.CandidateDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DayScheduler;
import com.example.japantravelplanner.itinerary.planner.DropReason;
import com.example.japantravelplanner.itinerary.planner.DroppedStop;
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 관광지 교체와 후보 조회 (저장소와 점수는 가짜, 시각 계산은 진짜) */
class ItineraryAttractionEditServiceTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final ItineraryStopRepository stopRepository = mock(ItineraryStopRepository.class);
    private final AttractionRepository attractionRepository = mock(AttractionRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final AccommodationLocator locator = mock(AccommodationLocator.class);
    private final AttractionScorer scorer = mock(AttractionScorer.class);
    private final PlaceTypeLabels typeLabels = mock(PlaceTypeLabels.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();

    private final Trip trip = trip();

    // 일정에 이미 들어 있는 곳 (id 11~14)
    private final Attraction parkNow = spot(11, "지금 공원", "park", 35.6851, 139.7100);          // 낮 1
    private final Attraction museumNow = spot(12, "지금 박물관", "museum", 35.6926, 139.7008);     // 낮 2
    private final Attraction deckNow = spot(13, "지금 전망대", "observation_deck", 35.6897, 139.6917);   // 야경
    private final Attraction dayTwoSpot = spot(14, "둘째 날 장소", "park", 35.7140, 139.7860);
    private final Restaurant lunch = restaurant(21, "점심집", "ラーメン");
    private final Restaurant dinner = restaurant(22, "저녁집", "洋食");

    // 후보 (아직 안 쓴 곳)
    private final Attraction nearPark = spot(31, "가까운 공원", "park", 35.6870, 139.7060);
    private final Attraction farMuseum = spot(32, "먼 박물관", "museum", 35.6605, 139.7390);
    private final Attraction otherZone = spot(33, "다른 지역 장소", "park", 35.7140, 139.7860);
    private final Attraction deckCandidate = spot(34, "다른 전망대", "observation_deck", 35.6897, 139.6917);
    private final Attraction farDeck = spot(35, "먼 전망대", "observation_deck", 35.6585, 139.7454);
    private final Attraction lowScore = spot(36, "점수 낮은 곳", "shopping_mall", 35.6930, 139.7010);

    private Itinerary latest;
    private ItineraryAttractionEditService service;

    private static Attraction spot(long id, String name, String types, double lat, double lng) {
        return Attraction.builder().id(id).name(name).types(types).rating(4.5).reviewCnt(1000).address("도쿄")
                .ward("新宿区").placeId("place-" + id).location(GF.createPoint(new Coordinate(lng, lat))).build();
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
        // 일부러 점수 순서를 섞어서 준다 (서비스가 점수 높은 순으로 정렬해야 한다)
        when(scorer.score(any(), any())).thenReturn(List.of(
                scored(lowScore, TripZone.SHINJUKU, 4.2),
                scored(nearPark, TripZone.SHINJUKU, 4.6),
                scored(farMuseum, TripZone.SHINJUKU, 4.5),
                scored(otherZone, TripZone.ASAKUSA_UENO, 4.9),
                scored(deckCandidate, TripZone.SHINJUKU, 4.4),
                scored(farDeck, TripZone.SHINJUKU, 4.3),
                scored(parkNow, TripZone.SHINJUKU, 4.8),          // 이미 일정에 있다
                scored(museumNow, TripZone.SHINJUKU, 4.7)));       // 이미 일정에 있다

        latest = itinerary("22:00", List.of(), List.of());
        registerStops();

        TravelTimeService travel = new TravelTimeService(new TravelProperties(), null);
        service = new ItineraryAttractionEditService(tripRepository, mock(TripPreferenceRepository.class),
                itineraryRepository, stopRepository, attractionRepository, restaurantRepository, locator, scorer,
                travel, new TravelProperties(), new DayScheduler(travel), typeLabels, lock, mock(ReasonContextLoader.class), transactionManager);
    }

    /** DAY1: 공원 → 점심 → 박물관 → 저녁 → 전망대(야경) / DAY2: 둘째 날 장소 → 저녁 */
    private Itinerary itinerary(String windowEnd, List<DroppedStop> dropped, List<String> warnings) throws Exception {
        ScheduledDay day1 = day(1, List.of(
                attractionStop(parkNow, "09:54", "11:09", 75),
                mealStopWithAlternatives(StopType.LUNCH, lunch, List.of(), "11:33", "12:33"),
                attractionStop(museumNow, "13:02", "14:32", 90),
                mealStopWithAlternatives(StopType.DINNER, dinner, List.of(), "18:00", "19:30"),
                attractionStop(deckNow, "19:58", "20:58", 60)), dropped, warnings);
        ScheduledDay day2 = day(2, List.of(
                        attractionStop(dayTwoSpot, "10:00", "11:15", 75),
                        mealStopWithAlternatives(StopType.DINNER, dinner, List.of(), "18:00", "19:30")),
                List.of(), List.of());
        Map<Long, Attraction> attractions = Map.of(11L, parkNow, 12L, museumNow, 13L, deckNow, 14L, dayTwoSpot);
        Map<Long, Restaurant> restaurants = Map.of(21L, lunch, 22L, dinner);
        Itinerary itinerary = ItineraryMapper.toEntity(trip, 1, 9.0, List.of(day1, day2),
                List.of(window("09:30", windowEnd), window("09:30", "22:00")),
                Map.of(1, "하루1 설명", 2, "하루2 설명"), attractions::get, restaurants::get);
        setId(itinerary, 100L);
        return itinerary;
    }

    private void registerStops() {
        List<ItineraryStop> stops = latest.getDays().get(0).getStops();
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(latest));
        when(stopRepository.findById(1L)).thenReturn(Optional.of(stops.get(0)));   // 낮 공원
        when(stopRepository.findById(2L)).thenReturn(Optional.of(stops.get(1)));   // 점심 (식당)
        when(stopRepository.findById(3L)).thenReturn(Optional.of(stops.get(2)));   // 낮 박물관
        when(stopRepository.findById(5L)).thenReturn(Optional.of(stops.get(4)));   // 야경 전망대
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private Itinerary savedItinerary() {
        ArgumentCaptor<Itinerary> captor = ArgumentCaptor.forClass(Itinerary.class);
        verify(itineraryRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static List<Long> ids(AttractionCandidatesResponseDto response) {
        return response.candidates().stream().map(CandidateDto::id).toList();
    }

    // ── 후보 조회 ──

    @Test
    void 후보는_같은_지역의_안_쓴_관광지만_점수_높은_순이다() {
        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, 1L);

        // 점수순: 가까운 공원 4.6, 먼 박물관 4.5, 다른 전망대 4.4, 먼 전망대 4.3, 점수 낮은 곳 4.2
        assertThat(ids(response)).containsExactly(31L, 32L, 34L, 35L, 36L);
        assertThat(ids(response)).doesNotContain(33L)               // 다른 지역
                .doesNotContain(11L, 12L, 13L, 14L);                // 이미 일정에 있는 곳(다른 날 포함)
        assertThat(response.night()).isFalse();
        assertThat(response.stopId()).isNull();                     // 테스트 자료의 항목은 저장 전이라 id가 없다
    }

    @Test
    void 후보에는_체류_시간과_앞_장소에서의_이동이_붙는다() {
        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, 1L);

        CandidateDto park = response.candidates().get(0);
        CandidateDto museum = response.candidates().get(1);
        CandidateDto deck = response.candidates().get(2);
        assertThat(park.stayMinutes()).isEqualTo(75);     // 공원
        assertThat(museum.stayMinutes()).isEqualTo(90);   // 박물관
        assertThat(deck.stayMinutes()).isEqualTo(60);     // 전망대
        assertThat(park.labels()).containsExactly("관광명소");
        assertThat(park.ward()).isEqualTo("新宿区");
        assertThat(park.placeId()).isEqualTo("place-31");
        assertThat(park.lat()).isEqualTo(35.6870);
        assertThat(park.lng()).isEqualTo(139.7060);
        assertThat(park.travel().mode()).isNotNull();
        assertThat(park.travel().minutes()).isPositive();
        // 숙소에서 가까운 공원이 먼 박물관보다 이동이 짧다
        assertThat(park.travel().minutes()).isLessThan(museum.travel().minutes());
    }

    @Test
    void 야경_장소를_바꿀_때는_전망대만_후보가_된다() {
        AttractionCandidatesResponseDto response = service.candidates(USER_ID, TRIP_ID, 5L);

        assertThat(response.night()).isTrue();
        assertThat(ids(response)).containsExactly(34L, 35L);   // 점수순 (다른 전망대 4.4, 먼 전망대 4.3)
    }

    @Test
    void 지금과_같은_자리의_전망대는_하루가_그대로라서_변화가_없다() {
        CandidateDto same = service.candidates(USER_ID, TRIP_ID, 5L).candidates().get(0);   // 지금 전망대와 같은 좌표

        assertThat(same.shiftMinutes()).isZero();
        assertThat(same.fitsInDay()).isTrue();
    }

    @Test
    void 먼_전망대로_바꾸면_하루가_늦게_끝나고_하루를_넘기면_표시한다() throws Exception {
        latest = itinerary("21:00", List.of(), List.of());   // 하루 끝 21:00
        registerStops();

        List<CandidateDto> candidates = service.candidates(USER_ID, TRIP_ID, 5L).candidates();
        CandidateDto near = candidates.get(0);   // 지금과 같은 자리
        CandidateDto far = candidates.get(1);    // 롯폰기 쪽

        assertThat(near.fitsInDay()).isTrue();
        assertThat(far.shiftMinutes()).isPositive();
        assertThat(far.fitsInDay()).isFalse();   // 21:00을 넘겨서 끝난다
    }

    // ── 오류 ──

    @Test
    void 식당_항목은_관광지_교체_대상이_아니다() {
        assertThatThrownBy(() -> service.candidates(USER_ID, TRIP_ID, 2L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("관광지");
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 2L, 31L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 남의_여행이거나_없는_항목은_찾을_수_없다() {
        assertThatThrownBy(() -> service.candidates(999L, TRIP_ID, 1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.candidates(USER_ID, TRIP_ID, 404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 이미_새_버전이_생긴_옛_항목은_충돌이다() throws Exception {
        Itinerary old = itinerary("22:00", List.of(), List.of());
        setId(old, 99L);   // 최신(100)이 아니다
        when(stopRepository.findById(88L)).thenReturn(Optional.of(old.getDays().get(0).getStops().get(0)));

        assertThatThrownBy(() -> service.candidates(USER_ID, TRIP_ID, 88L))
                .isInstanceOf(ConflictException.class).hasMessageContaining("새로고침");
    }

    // ── 교체 ──

    @Test
    void 교체하면_새_버전_EDITED로_저장되고_변경_설명이_남는다() {
        ItineraryResponseDto response = service.replace(USER_ID, TRIP_ID, 1L, 31L);

        Itinerary saved = savedItinerary();
        assertThat(saved.getVersionNo()).isEqualTo(2);
        assertThat(saved.getSource()).isEqualTo(ItinerarySource.EDITED);
        assertThat(saved.getChangeNote()).isEqualTo("DAY1 관광지: 지금 공원 → 가까운 공원");
        assertThat(saved.getKrwPerYen()).isEqualTo(9.0);

        ItineraryStop first = saved.getDays().get(0).getStops().get(0);
        assertThat(first.getAttraction().getId()).isEqualTo(31L);
        // 이전 버전은 그대로
        assertThat(latest.getDays().get(0).getStops().get(0).getAttraction().getId()).isEqualTo(11L);
        assertThat(latest.getVersionNo()).isEqualTo(1);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.source()).isEqualTo(ItinerarySource.EDITED);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.EDITING);
    }

    @Test
    void 바뀐_날의_순서와_개수는_그대로이고_다른_날과_식당은_건드리지_않는다() {
        service.replace(USER_ID, TRIP_ID, 1L, 31L);

        Itinerary saved = savedItinerary();
        List<ItineraryStop> stops = saved.getDays().get(0).getStops();
        assertThat(stops).extracting(ItineraryStop::getType).containsExactly(
                StopType.ATTRACTION, StopType.LUNCH, StopType.ATTRACTION, StopType.DINNER, StopType.ATTRACTION);
        assertThat(stops.get(1).getRestaurant().getId()).isEqualTo(21L);   // 점심 식당은 그대로
        assertThat(stops.get(3).getRestaurant().getId()).isEqualTo(22L);
        assertThat(stops.get(2).getAttraction().getId()).isEqualTo(12L);   // 다른 관광지도 그대로
        assertThat(stops.get(4).getAttraction().getId()).isEqualTo(13L);
        for (int i = 1; i < stops.size(); i++) {
            assertThat(stops.get(i).getArrivalTime()).isAfter(stops.get(i - 1).getDepartureTime());   // 시각이 이어진다
        }
        // 다른 날은 그대로 복사
        assertThat(saved.getDays().get(1).getStops().get(0).getAttraction().getId()).isEqualTo(14L);
        assertThat(saved.getDays().get(1).getDescription()).isEqualTo("하루2 설명");
    }

    @Test
    void 바뀐_날의_AI_설명은_비운다() {
        service.replace(USER_ID, TRIP_ID, 1L, 31L);

        assertThat(savedItinerary().getDays().get(0).getDescription()).isNull();   // 장소 이름이 달라져서
    }

    @Test
    void 체류_시간은_새_장소의_종류로_다시_계산한다() {
        // 박물관(90분) 자리에 공원(75분)을 넣는다
        service.replace(USER_ID, TRIP_ID, 3L, 31L);

        ItineraryStop replaced = savedItinerary().getDays().get(0).getStops().get(2);
        assertThat(replaced.getAttraction().getId()).isEqualTo(31L);
        assertThat(replaced.getStayMinutes()).isEqualTo(75);
        assertThat(java.time.Duration.between(replaced.getArrivalTime(), replaced.getDepartureTime()).toMinutes())
                .isEqualTo(75);
    }

    @Test
    void 야경_장소를_바꾸면_저녁_뒤_마지막_항목으로_남는다() {
        service.replace(USER_ID, TRIP_ID, 5L, 34L);

        List<ItineraryStop> stops = savedItinerary().getDays().get(0).getStops();
        assertThat(stops.get(4).getAttraction().getId()).isEqualTo(34L);
        assertThat(stops.get(4).getStayMinutes()).isEqualTo(60);
        assertThat(savedItinerary().getChangeNote()).isEqualTo("DAY1 야경: 지금 전망대 → 다른 전망대");
    }

    @Test
    void 시간이_모자라서_뺐던_곳을_직접_고르면_뺀_곳_목록에서_지운다() throws Exception {
        latest = itinerary("22:00", List.of(new DroppedStop("가까운 공원", DropReason.TIME_SHORTAGE),
                new DroppedStop("그대로 뺀 곳", DropReason.TIME_SHORTAGE)), List.of());
        registerStops();

        service.replace(USER_ID, TRIP_ID, 1L, 31L);

        assertThat(savedItinerary().getDays().get(0).getDropped())
                .extracting(d -> d.getName()).containsExactly("그대로 뺀 곳");
    }

    @Test
    void 시간이_넘쳐도_항목을_빼지_않고_안내_문장으로만_알린다() throws Exception {
        latest = itinerary("21:00", List.of(), List.of());
        registerStops();

        service.replace(USER_ID, TRIP_ID, 5L, 35L);   // 롯폰기 쪽 먼 전망대

        ItineraryStop[] stops = savedItinerary().getDays().get(0).getStops().toArray(new ItineraryStop[0]);
        assertThat(stops).hasSize(5);   // 줄이지 않는다
        assertThat(savedItinerary().getDays().get(0).getWarnings()).anyMatch(w -> w.startsWith("일정이 21:00"));
    }

    @Test
    void 이전의_시간_안내는_새로_계산하고_다른_안내는_그대로_둔다() throws Exception {
        latest = itinerary("22:00", List.of(),
                List.of("저녁 시간이 20:00을 넘겼어요.", "스파이스는 이 날 정기 휴일이에요. 영업하는지 확인해주세요."));
        registerStops();

        service.replace(USER_ID, TRIP_ID, 1L, 31L);

        List<String> warnings = savedItinerary().getDays().get(0).getWarnings();
        assertThat(warnings).noneMatch(w -> w.startsWith("저녁 시간이"));          // 옛 시간 안내는 사라지고
        assertThat(warnings).anyMatch(w -> w.contains("정기 휴일"));              // 휴일 안내는 남는다
    }

    @Test
    void 후보에_없는_관광지는_바꿀_수_없다() {
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 33L))   // 다른 지역
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("넣을 수 없는");
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 14L))   // 다른 날에 이미 쓴 곳
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 999L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 낮_자리에는_야경용_후보_제한이_없고_야경_자리에는_일반_관광지를_못_넣는다() {
        service.replace(USER_ID, TRIP_ID, 1L, 34L);   // 낮 자리에 전망대는 괜찮다
        assertThat(savedItinerary().getDays().get(0).getStops().get(0).getAttraction().getId()).isEqualTo(34L);

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 5L, 31L))   // 야경 자리에 공원은 안 된다
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 지금_정해진_관광지와_같으면_바꿀_수_없다() {
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 11L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이미");
    }

    // ── 동시성과 실패 ──

    @Test
    void 저장_충돌은_409로_안내하고_잠금은_풀린다() {
        when(itineraryRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_itineraries_trip_version"));

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 31L)).isInstanceOf(ConflictException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    @Test
    void 버전_충돌이_아닌_DB_거절은_409로_바꾸지_않는다() {
        when(itineraryRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("violates check constraint \"x\""));

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 31L))
                .isInstanceOf(DataIntegrityViolationException.class).isNotInstanceOf(ConflictException.class);
    }

    @Test
    void 다른_작업_중이면_바꿀_수_없고_그_잠금은_건드리지_않는다() {
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();

        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 1L, 31L)).isInstanceOf(ConflictException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isFalse();
    }

    @Test
    void 실패한_요청도_잠금을_풀어준다() {
        assertThatThrownBy(() -> service.replace(USER_ID, TRIP_ID, 2L, 31L))   // 식당 항목이라 실패
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }
}