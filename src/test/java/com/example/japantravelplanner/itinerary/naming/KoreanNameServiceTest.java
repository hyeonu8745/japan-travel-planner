package com.example.japantravelplanner.itinerary.naming;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.planner.DayWindow;
import com.example.japantravelplanner.itinerary.planner.PlannedMeal;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.ScheduledStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.service.ItineraryMapper;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/** 한국어 이름: 일정에 처음 쓰일 때 한 번 만들어 저장, 실패해도 일정은 정상 (AI는 가짜) */
class KoreanNameServiceTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final KoreanNameTranslator translator = mock(KoreanNameTranslator.class);
    private final AttractionRepository attractionRepository = mock(AttractionRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final KoreanNameProperties props = new KoreanNameProperties();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-11T00:00:00Z"));
    private KoreanNameService service;

    private Attraction sensoji;
    private Restaurant chosen;
    private Restaurant alternative;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new KoreanNameService(translator, attractionRepository, restaurantRepository, props,
                transactionManager, clock);
        sensoji = Attraction.builder().id(1L).name("浅草寺").types("buddhist_temple")
                .location(GF.createPoint(new Coordinate(139.7967, 35.7148))).build();
        chosen = Restaurant.builder().id(10L).name("とんかつ まい泉").genre("とんかつ")
                .location(GF.createPoint(new Coordinate(139.79, 35.71))).build();
        alternative = Restaurant.builder().id(11L).name("つけ麺 五ノ神製作所").genre("つけ麺")
                .location(GF.createPoint(new Coordinate(139.79, 35.71))).build();
    }

    private ItineraryResponseDto response() {
        PlannedMeal meal = new PlannedMeal(Meal.LUNCH, new TravelPoint("기준", 35.71, 139.79),
                new RestaurantCandidate(chosen, 100, 4.0, false),
                List.of(new RestaurantCandidate(alternative, 200, 3.9, false)), false);
        ScheduledDay day = new ScheduledDay(1, LocalDate.of(2026, 10, 20), TripZone.ASAKUSA_UENO, List.of(
                new ScheduledStop(StopType.ATTRACTION, new ScoredAttraction(sensoji, TripZone.ASAKUSA_UENO, 4.5), null,
                        LocalTime.of(10, 0), LocalTime.of(11, 0), 50, 0, new TravelLeg(5, 300, LegMode.WALK, false)),
                new ScheduledStop(StopType.LUNCH, null, meal, LocalTime.of(12, 0), LocalTime.of(13, 0), 60, 0,
                        new TravelLeg(5, 300, LegMode.WALK, false))), List.of(), List.of());
        var itinerary = ItineraryMapper.toEntity(Trip.builder().id(5L).build(), 1, 9.0, List.of(day),
                List.of(new DayWindow(LocalTime.of(9, 30), LocalTime.of(21, 0), false, true)), Map.of(),
                id -> sensoji, id -> chosen);
        return ItineraryResponseDto.from(itinerary, t -> List.of(), Map.of(11L, alternative));
    }

    @Test
    void 일정_생성_때_이름이_없는_곳만_만들어_저장하고_객체에도_넣는다() {
        alternative.setNameKo("츠케멘 고노카미 제작소");   // 이미 있는 곳은 묻지 않는다
        when(translator.translate(anyMap())).thenReturn(Map.of("a1", "센소지", "r10", "돈카츠 마이센"));

        service.ensure(List.of(sensoji), List.of(chosen, alternative));

        verify(translator).translate(Map.of("a1", "浅草寺", "r10", "とんかつ まい泉"));
        verify(attractionRepository).saveNameKo(1L, "센소지");
        verify(restaurantRepository).saveNameKo(10L, "돈카츠 마이센");
        verify(restaurantRepository, never()).saveNameKo(eq(11L), any());
        assertThat(sensoji.getNameKo()).isEqualTo("센소지");
        assertThat(chosen.getNameKo()).isEqualTo("돈카츠 마이센");
    }

    @Test
    void 응답의_빈_이름을_고른_곳과_다음_후보까지_채운다() {
        when(translator.translate(anyMap())).thenReturn(Map.of("a1", "센소지", "r10", "돈카츠 마이센",
                "r11", "츠케멘 고노카미 제작소"));

        ItineraryResponseDto filled = service.fill(response());

        var stops = filled.days().get(0).stops();
        assertThat(stops.get(0).nameKo()).isEqualTo("센소지");
        assertThat(stops.get(0).name()).isEqualTo("浅草寺");   // 원래 이름은 그대로 (화면에 작게)
        assertThat(stops.get(1).nameKo()).isEqualTo("돈카츠 마이센");
        assertThat(stops.get(1).restaurant().genreLabels()).containsExactly("돈카츠");
        assertThat(stops.get(1).alternatives().get(0).nameKo()).isEqualTo("츠케멘 고노카미 제작소");
        assertThat(stops.get(1).alternatives().get(0).genreLabels()).containsExactly("츠케멘");
    }

    @Test
    void 이름이_모두_있으면_AI를_부르지_않는다() {
        sensoji.setNameKo("센소지");
        chosen.setNameKo("돈카츠 마이센");
        alternative.setNameKo("츠케멘 고노카미 제작소");

        ItineraryResponseDto same = response();

        assertThat(service.fill(same)).isSameAs(same);
        verifyNoInteractions(translator);
    }

    @Test
    void 실패한_이름은_한동안_다시_묻지_않는다() {
        when(translator.translate(anyMap())).thenReturn(Map.of());   // AI 실패 = 빈 결과

        ItineraryResponseDto first = service.fill(response());
        service.fill(response());
        clock.advance(Duration.ofMinutes(props.getRetryAfterMinutes() + 1));
        service.fill(response());

        assertThat(first.days().get(0).stops().get(0).nameKo()).isNull();   // 이름 없이도 일정은 정상
        verify(translator, times(2)).translate(anyMap());
        verifyNoInteractions(attractionRepository);
    }

    @Test
    void 꺼져_있으면_만들지_않는다() {
        props.setEnabled(false);

        service.ensure(List.of(sensoji), List.of(chosen));

        verifyNoInteractions(translator);
        assertThat(sensoji.getNameKo()).isNull();
    }

    /** 시간을 앞으로 돌릴 수 있는 시계 */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
