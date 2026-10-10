package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.candidate.AttractionScorer;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.AddAttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto.CandidateDto;
import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
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

/**
 * 하루 일정의 항목 삭제·순서 변경·관광지 추가.
 * 저장소·점수는 가짜(Mockito), 이동 시간과 시각 계산은 진짜 코드로 확인한다.
 */
class ItineraryStopEditServiceTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final ItineraryStopRepository stopRepository = mock(ItineraryStopRepository.class);
    private final AttractionScorer scorer = mock(AttractionScorer.class);
    private final AccommodationLocator locator = mock(AccommodationLocator.class);
    private final PlaceTypeLabels typeLabels = mock(PlaceTypeLabels.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();

    private final Trip trip = trip();

    // 일정에 들어 있는 곳
    private final Attraction parkNow = spot(11, "지금 공원", "park", 35.6851, 139.7100);
    private final Attraction museumNow = spot(12, "지금 박물관", "museum", 35.6926, 139.7008);
    private final Attraction deckNow = spot(13, "지금 전망대", "observation_deck", 35.6897, 139.6917);
    private final Attraction dayTwoSpot = spot(14, "둘째 날 장소", "park", 35.7140, 139.7860);
    private final Restaurant lunch = restaurant(21, "점심집", "ラーメン");
    private final Restaurant dinner = restaurant(22, "저녁집", "洋食");

    // 아직 안 쓴 곳
    private final Attraction nearPark = spot(31, "가까운 공원", "park", 35.6870, 139.7060);
    private final Attraction farMuseum = spot(32, "먼 박물관", "museum", 35.6605, 139.7390);
    private final Attraction otherZone = spot(33, "다른 지역 장소", "park", 35.7145, 139.7865);   // 둘째 날 장소 바로 옆
    private final Attraction deckCandidate = spot(34, "다른 전망대", "observation_deck", 35.6897, 139.6917);
    private final Attraction lowScore = spot(36, "점수 낮은 곳", "shopping_mall", 35.6930, 139.7010);

    private Itinerary latest;
    private ItineraryStopEditService service;

    private static Attraction spot(long id, String name, String types, double lat, double lng) {
        return Attraction.builder().id(id).name(name).types(types).rating(4.5).reviewCnt(1000).address("도쿄")
                .ward("新宿区").placeId("place-" + id).location(GF.createPoint(new Coordinate(lng, lat))).build();
    }

    @BeforeEach
    void setUp() throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(locator.locate(trip)).thenReturn(GF.createPoint(new Coordinate(139.7020, 35.6951)));
        when(typeLabels.toVisibleLabels(any())).thenReturn(List.of("관광명소"));
        when(itineraryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        // 점수 순서를 섞어서 준다 (서비스가 점수 높은 순으로 정렬해야 한다)
        when(scorer.score(any(), any())).thenReturn(List.of(
                new ScoredAttraction(lowScore, TripZone.SHINJUKU, 4.2),
                new ScoredAttraction(nearPark, TripZone.SHINJUKU, 4.6),
                new ScoredAttraction(farMuseum, TripZone.SHINJUKU, 4.5),
                new ScoredAttraction(otherZone, TripZone.ASAKUSA_UENO, 4.9),
                new ScoredAttraction(deckCandidate, TripZone.SHINJUKU, 4.4),
                new ScoredAttraction(parkNow, TripZone.SHINJUKU, 4.8)));   // 이미 일정에 있다

        use(itinerary(List.of(), List.of()));

        TravelTimeService travel = new TravelTimeService(new TravelProperties(), null);
        service = new ItineraryStopEditService(tripRepository, mock(TripPreferenceRepository.class),
                itineraryRepository, stopRepository, mock(AttractionRepository.class), mock(RestaurantRepository.class),
                locator, scorer, travel, new TravelProperties(), new DayScheduler(travel), typeLabels, lock,
                mock(ReasonContextLoader.class), transactionManager);
    }

    /**
     * DAY1: 공원(1) → 점심(2) → 박물관(3) → 저녁(4) → 전망대(5) / DAY2: 둘째 날 장소(6) → 저녁(7)
     * 괄호 안은 항목 id. 저장된 "같은 음식" 표시는 모두 false로 시작한다.
     */
    private Itinerary itinerary(List<DroppedStop> dropped, List<String> warnings) throws Exception {
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
                List.of(window("09:30", "22:00"), window("09:30", "22:00")),
                Map.of(1, "하루1 설명", 2, "하루2 설명"), attractions::get, restaurants::get);
        setId(itinerary, 100L);
        long id = 1;
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop stop : day.getStops()) {
                setId(stop, id++);
            }
        }
        return itinerary;
    }

    /** 이 일정을 최신 버전으로 쓰고, 항목 id로 찾을 수 있게 한다 */
    private void use(Itinerary itinerary) {
        latest = itinerary;
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(itinerary));
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop stop : day.getStops()) {
                when(stopRepository.findById(stop.getId())).thenReturn(Optional.of(stop));
            }
        }
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

    private static List<String> names(ItineraryDay day) {
        return day.getStops().stream()
                .map(s -> s.getAttraction() != null ? s.getAttraction().getName() : s.getRestaurant().getName())
                .toList();
    }

    /** 순서대로 이어지는지 (앞 항목이 끝난 뒤에 다음 항목에 도착) */
    private static void assertTimesFlow(ItineraryDay day) {
        List<ItineraryStop> stops = day.getStops();
        for (int i = 1; i < stops.size(); i++) {
            assertThat(stops.get(i).getArrivalTime()).isAfter(stops.get(i - 1).getDepartureTime());
        }
        assertThat(stops).extracting(ItineraryStop::getStopOrder)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, stops.size()).boxed().toList());
    }

    // ── 삭제 ──

    @Test
    void 관광지를_지우면_남은_순서로_시각을_다시_계산해_EDITED로_저장한다() {
        service.deleteStop(USER_ID, TRIP_ID, 3L);

        Itinerary saved = savedItinerary();
        assertThat(saved.getVersionNo()).isEqualTo(2);
        assertThat(saved.getSource()).isEqualTo(ItinerarySource.EDITED);
        assertThat(saved.getChangeNote()).isEqualTo("DAY1 관광지 삭제: 지금 박물관");

        ItineraryDay day1 = saved.getDays().get(0);
        assertThat(names(day1)).containsExactly("지금 공원", "점심집", "저녁집", "지금 전망대");
        assertTimesFlow(day1);
        assertThat(day1.getDescription()).isNull();                              // 장소가 달라져서 설명은 비운다
        assertThat(saved.getDays().get(1).getDescription()).isEqualTo("하루2 설명");   // 다른 날은 그대로
        assertThat(trip.getStatus()).isEqualTo(TripStatus.EDITING);
    }

    @Test
    void 점심을_지우면_그_식당의_정기_휴일_안내도_함께_지운다() throws Exception {
        use(itinerary(List.of(), List.of("점심집은(는) 이 날이 정기 휴일이에요. 영업하는지 확인해주세요.", "다른 안내")));

        service.deleteStop(USER_ID, TRIP_ID, 2L);

        ItineraryDay day1 = savedItinerary().getDays().get(0);
        assertThat(names(day1)).containsExactly("지금 공원", "지금 박물관", "저녁집", "지금 전망대");
        assertThat(day1.getWarnings()).contains("다른 안내").noneMatch(w -> w.startsWith("점심집"));
        assertThat(savedItinerary().getChangeNote()).isEqualTo("DAY1 점심 삭제: 점심집");
    }

    @Test
    void 끼니를_지우면_뒤_끼니의_같은_음식_표시를_다시_판단한다() {
        // 지우기 전: 점심(라멘) → 저녁(양식) → 둘째 날 저녁(양식). 첫날 저녁을 지우면 둘째 날 저녁의 앞 끼니는 라멘이다
        service.deleteStop(USER_ID, TRIP_ID, 4L);

        Itinerary saved = savedItinerary();
        assertThat(saved.getDays().get(1).getStops().get(1).isGenreRepeated()).isFalse();
    }

    @Test
    void 끼니를_지워서_같은_음식이_이어지면_표시한다() {
        // 첫날 점심(라멘)을 지우면 첫날 저녁(양식) 다음 둘째 날 저녁(양식)이 같은 음식이 된다
        service.deleteStop(USER_ID, TRIP_ID, 2L);

        Itinerary saved = savedItinerary();
        assertThat(saved.getDays().get(0).getStops().get(2).isGenreRepeated()).isFalse();   // 첫날 저녁
        assertThat(saved.getDays().get(1).getStops().get(1).isGenreRepeated()).isTrue();    // 둘째 날 저녁
    }

    @Test
    void 하루에_하나뿐인_항목은_지울_수_없다() throws Exception {
        ScheduledDay only = day(1, List.of(attractionStop(parkNow, "10:00", "11:15", 75)), List.of(), List.of());
        Itinerary single = ItineraryMapper.toEntity(trip, 1, 9.0, List.of(only), List.of(window("09:30", "22:00")),
                Map.of(), Map.of(11L, parkNow)::get, Map.<Long, Restaurant>of()::get);
        setId(single, 100L);
        setId(single.getDays().get(0).getStops().get(0), 1L);
        use(single);

        assertThatThrownBy(() -> service.deleteStop(USER_ID, TRIP_ID, 1L)).isInstanceOf(IllegalArgumentException.class);
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 옛_버전의_항목을_지우려_하면_409() throws Exception {
        Itinerary old = itinerary(List.of(), List.of());
        setId(old, 99L);
        ItineraryStop oldStop = old.getDays().get(0).getStops().get(0);
        setId(oldStop, 50L);
        when(stopRepository.findById(50L)).thenReturn(Optional.of(oldStop));

        assertThatThrownBy(() -> service.deleteStop(USER_ID, TRIP_ID, 50L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void 없는_항목이나_남의_여행은_404() {
        assertThatThrownBy(() -> service.deleteStop(USER_ID, TRIP_ID, 999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteStop(999L, TRIP_ID, 3L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 다른_요청이_일정을_고치는_중이면_409() {
        lock.tryAcquire(TRIP_ID);

        assertThatThrownBy(() -> service.deleteStop(USER_ID, TRIP_ID, 3L)).isInstanceOf(ConflictException.class);
    }

    // ── 순서 변경 ──

    @Test
    void 순서를_바꾸면_그_순서로_시각을_다시_계산해_저장한다() {
        service.reorder(USER_ID, TRIP_ID, 1, List.of(3L, 2L, 1L, 4L, 5L), 1);

        Itinerary saved = savedItinerary();
        assertThat(saved.getChangeNote()).isEqualTo("DAY1 순서 변경");
        ItineraryDay day1 = saved.getDays().get(0);
        assertThat(names(day1)).containsExactly("지금 박물관", "점심집", "지금 공원", "저녁집", "지금 전망대");
        assertThat(day1.getStops().get(0).getStayMinutes()).isEqualTo(90);   // 체류 시간은 장소를 따라간다
        assertTimesFlow(day1);
        assertThat(names(saved.getDays().get(1))).containsExactly("둘째 날 장소", "저녁집");
    }

    @Test
    void 시간이_안_맞는_순서도_막지_않고_안내만_한다() {
        // 점심을 저녁 뒤로 보내면 점심이 13:30을 넘는다
        service.reorder(USER_ID, TRIP_ID, 1, List.of(1L, 3L, 4L, 2L, 5L), null);

        ItineraryDay day1 = savedItinerary().getDays().get(0);
        assertThat(day1.getStops()).hasSize(5);   // 아무것도 빼지 않는다
        assertThat(day1.getWarnings()).contains("점심 시간이 13:30을 넘겼어요.");
    }

    @Test
    void 그날_항목을_빠짐없이_한_번씩_보내지_않으면_400() {
        assertThatThrownBy(() -> service.reorder(USER_ID, TRIP_ID, 1, List.of(1L, 2L, 3L, 4L), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reorder(USER_ID, TRIP_ID, 1, List.of(1L, 1L, 2L, 3L, 4L), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reorder(USER_ID, TRIP_ID, 1, List.of(1L, 2L, 3L, 4L, 6L), null))
                .isInstanceOf(IllegalArgumentException.class);   // 다른 날의 항목
    }

    @Test
    void 지금과_같은_순서면_400() {
        assertThatThrownBy(() -> service.reorder(USER_ID, TRIP_ID, 1, List.of(1L, 2L, 3L, 4L, 5L), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 화면이_옛_버전을_보고_있으면_409() {
        assertThatThrownBy(() -> service.reorder(USER_ID, TRIP_ID, 1, List.of(3L, 2L, 1L, 4L, 5L), 0))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void 옛_버전의_항목_id로_순서를_보내면_409() throws Exception {
        Itinerary old = itinerary(List.of(), List.of());
        setId(old, 99L);
        ItineraryStop oldStop = old.getDays().get(0).getStops().get(0);
        setId(oldStop, 50L);
        when(stopRepository.findById(50L)).thenReturn(Optional.of(oldStop));

        assertThatThrownBy(() -> service.reorder(USER_ID, TRIP_ID, 1, List.of(50L, 2L, 3L, 4L, 5L), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void 없는_날짜는_404() {
        assertThatThrownBy(() -> service.reorder(USER_ID, TRIP_ID, 9, List.of(1L), null))
                .isInstanceOf(NotFoundException.class);
    }

    // ── 관광지 추가 ──

    @Test
    void 추가_후보는_같은_지역의_안_쓴_관광지만_점수_높은_순이다() {
        // 맨 뒤(야경 뒤)에 넣는 경우: 저녁 전 대기 시간에 흡수되지 않으므로 그날이 늦게 끝난다
        AddAttractionCandidatesResponseDto response = service.addCandidates(USER_ID, TRIP_ID, 1, 5);

        assertThat(response.dayNumber()).isEqualTo(1);
        assertThat(response.position()).isEqualTo(5);
        assertThat(response.broadened()).isFalse();
        assertThat(response.candidates()).extracting(CandidateDto::id).containsExactly(31L, 32L, 34L, 36L);
        assertThat(response.candidates()).allMatch(c -> c.shiftMinutes() > 0);
        assertThat(response.candidates()).allMatch(CandidateDto::sameZone);
    }

    @Test
    void 같은_지역_후보가_적으면_넣을_자리_근처_3km_안의_다른_지역도_준다() {
        when(scorer.score(any(), any())).thenReturn(List.of(
                new ScoredAttraction(nearPark, TripZone.SHINJUKU, 4.6),
                new ScoredAttraction(otherZone, TripZone.ASAKUSA_UENO, 4.9)));

        // 둘째 날 장소 바로 뒤: 다른 지역 장소가 바로 옆이다
        AddAttractionCandidatesResponseDto nearby = service.addCandidates(USER_ID, TRIP_ID, 2, 1);
        assertThat(nearby.candidates()).extracting(CandidateDto::id).containsExactly(31L, 33L);
        assertThat(nearby.broadened()).isTrue();

        // 첫날 맨 앞(기준: 첫 장소인 공원): 다른 지역 장소는 3km보다 멀다
        AddAttractionCandidatesResponseDto far = service.addCandidates(USER_ID, TRIP_ID, 1, 0);
        assertThat(far.candidates()).extracting(CandidateDto::id).containsExactly(31L);
        assertThat(far.broadened()).isFalse();
    }

    @Test
    void 관광지를_넣으면_그_자리에_들어가고_시간이_부족해_뺀_곳_목록에서_지운다() throws Exception {
        use(itinerary(List.of(new DroppedStop("가까운 공원", DropReason.TIME_SHORTAGE)), List.of()));

        service.addAttraction(USER_ID, TRIP_ID, 1, 31L, 1, 1);

        Itinerary saved = savedItinerary();
        assertThat(saved.getChangeNote()).isEqualTo("DAY1 관광지 추가: 가까운 공원");
        ItineraryDay day1 = saved.getDays().get(0);
        assertThat(names(day1)).containsExactly("지금 공원", "가까운 공원", "점심집", "지금 박물관", "저녁집", "지금 전망대");
        ItineraryStop added = day1.getStops().get(1);
        assertThat(added.getType()).isEqualTo(StopType.ATTRACTION);
        assertThat(added.getStayMinutes()).isEqualTo(75);   // 공원 체류 시간 (보통 속도)
        assertThat(added.getAlternativeRestaurantIds()).isEmpty();
        assertTimesFlow(day1);
        assertThat(day1.getDropped()).extracting(DroppedPlace::getName).doesNotContain("가까운 공원");
    }

    @Test
    void 맨_뒤에도_넣을_수_있다() {
        service.addAttraction(USER_ID, TRIP_ID, 2, 31L, 2, null);

        assertThat(names(savedItinerary().getDays().get(1))).containsExactly("둘째 날 장소", "저녁집", "가까운 공원");
    }

    @Test
    void 후보가_아닌_관광지는_넣을_수_없다() {
        // 이미 일정에 있는 곳
        assertThatThrownBy(() -> service.addAttraction(USER_ID, TRIP_ID, 1, 11L, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(itineraryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 넣을_자리가_범위를_벗어나면_400() {
        assertThatThrownBy(() -> service.addAttraction(USER_ID, TRIP_ID, 1, 31L, 6, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addCandidates(USER_ID, TRIP_ID, 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 관광지를_넣을_때도_옛_버전을_보고_있으면_409() {
        assertThatThrownBy(() -> service.addAttraction(USER_ID, TRIP_ID, 1, 31L, 1, 0))
                .isInstanceOf(ConflictException.class);
    }
}
