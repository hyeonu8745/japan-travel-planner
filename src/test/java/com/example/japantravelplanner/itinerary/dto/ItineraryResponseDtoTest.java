package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
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
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 일정 응답에 늘어난 필드(숙소, 장소 상세, 식당 대안). 다른 테스트 자료에 기대지 않고 직접 만든다 */
class ItineraryResponseDtoExtendedTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final Attraction garden = Attraction.builder().id(64L).name("新宿御苑").types("park")
            .rating(4.6).reviewCnt(45733).address("東京都新宿区内藤町11").ward("新宿区").placeId("place-64")
            .location(GF.createPoint(new Coordinate(139.7100, 35.6851))).build();
    private final Restaurant chosen = restaurant(1L, "つけ麺 五ノ神製作所", "つけ麺", 3.78);
    private final Restaurant second = restaurant(2L, "風雲児 新宿本店", "ラーメン,つけ麺", 3.85);
    private final Restaurant third = restaurant(3L, "麺屋海神 新宿店", "ラーメン,丼もの（その他）", 3.72);

    private static Restaurant restaurant(long id, String name, String genre, double rating) {
        return Restaurant.builder().id(id).name(name).genre(genre).ratingVal(rating)
                .budgetLunch("～￥999").budgetDinner("￥1,000～￥1,999").address("東京都新宿区" + id)
                .reviewCnt(1500).nearestStation("新宿駅").nearestDistance(320).holiday("日曜日")
                .location(GF.createPoint(new Coordinate(139.70 + id / 1000.0, 35.69))).build();
    }

    private static ScheduledStop attractionStop(Attraction a) {
        return new ScheduledStop(StopType.ATTRACTION, new ScoredAttraction(a, TripZone.SHINJUKU, 4.5), null,
                LocalTime.of(16, 15), LocalTime.of(16, 55), 40, 0, new TravelLeg(5, 376, LegMode.WALK, false));
    }

    private static ScheduledStop dinnerStop(Restaurant chosen, List<Restaurant> alternatives) {
        PlannedMeal meal = new PlannedMeal(Meal.DINNER, new TravelPoint("기준", 35.69, 139.70),
                new RestaurantCandidate(chosen, 270, 3.7, false),
                alternatives.stream().map(r -> new RestaurantCandidate(r, 400, 3.6, false)).toList(), false);
        return new ScheduledStop(StopType.DINNER, null, meal, LocalTime.of(18, 59), LocalTime.of(20, 29), 90, 0,
                new TravelLeg(10, 763, LegMode.WALK, false));
    }

    private Itinerary itinerary(Trip trip, List<Restaurant> alternatives) {
        ScheduledDay day = new ScheduledDay(1, LocalDate.of(2026, 10, 20), TripZone.SHINJUKU,
                List.of(attractionStop(garden), dinnerStop(chosen, alternatives)), List.of(), List.of());
        return ItineraryMapper.toEntity(trip, 1, 9.0, List.of(day),
                List.of(new DayWindow(LocalTime.of(16, 0), LocalTime.of(22, 0), false, true)), Map.of(),
                id -> garden, id -> chosen);
    }

    private static Trip tripWithHotel() {
        Trip trip = Trip.builder().id(5L).accommodationName("테스트 호텔")
                .accommodationAddress("東京都新宿区歌舞伎町1-19-1").build();
        trip.setAccommodationLocation(GF.createPoint(new Coordinate(139.7030, 35.6944)));
        return trip;
    }

    @Test
    void 숙소는_이름_주소_좌표를_담는다() {
        ItineraryResponseDto.AccommodationDto hotel =
                ItineraryResponseDto.from(itinerary(tripWithHotel(), List.of()), t -> List.of()).accommodation();

        assertThat(hotel.name()).isEqualTo("테스트 호텔");
        assertThat(hotel.address()).isEqualTo("東京都新宿区歌舞伎町1-19-1");
        // JTS 좌표는 (경도, 위도) 순서라서 y가 위도
        assertThat(hotel.lat()).isEqualTo(35.6944);
        assertThat(hotel.lng()).isEqualTo(139.7030);
    }

    @Test
    void 숙소_좌표를_아직_못_찾았으면_좌표만_비어_있다() {
        Trip trip = Trip.builder().id(5L).accommodationName("테스트 호텔").build();

        ItineraryResponseDto.AccommodationDto hotel =
                ItineraryResponseDto.from(itinerary(trip, List.of()), t -> List.of()).accommodation();

        assertThat(hotel.name()).isEqualTo("테스트 호텔");
        assertThat(hotel.lat()).isNull();
        assertThat(hotel.lng()).isNull();
    }

    @Test
    void 관광지는_구와_구글_장소_id를_담는다() {
        ItineraryResponseDto.AttractionDto attraction =
                ItineraryResponseDto.from(itinerary(tripWithHotel(), List.of()), t -> List.of("공원"))
                        .days().get(0).stops().get(0).attraction();

        assertThat(attraction.ward()).isEqualTo("新宿区");
        assertThat(attraction.placeId()).isEqualTo("place-64");
    }

    @Test
    void 식당은_가까운_역과_정기_휴일을_담는다() {
        ItineraryResponseDto.RestaurantDto restaurant =
                ItineraryResponseDto.from(itinerary(tripWithHotel(), List.of()), t -> List.of())
                        .days().get(0).stops().get(1).restaurant();

        assertThat(restaurant.reviewCount()).isEqualTo(1500);
        assertThat(restaurant.nearestStation()).isEqualTo("新宿駅");
        assertThat(restaurant.nearestDistance()).isEqualTo(320);
        assertThat(restaurant.holiday()).isEqualTo("日曜日");
        assertThat(restaurant.budget()).isEqualTo("￥1,000～￥1,999");   // 저녁이라 저녁 예산
    }

    @Test
    void 식당_대안은_저장된_순서대로_좌표와_함께_나가고_관광지는_빈_목록() {
        Itinerary itinerary = itinerary(tripWithHotel(), List.of(second, third));
        ItineraryResponseDto dto = ItineraryResponseDto.from(itinerary, t -> List.of(),
                Map.of(2L, second, 3L, third));

        List<ItineraryResponseDto.StopDto> stops = dto.days().get(0).stops();
        assertThat(stops.get(0).alternatives()).isEmpty();
        assertThat(stops.get(1).alternatives()).extracting(ItineraryResponseDto.AlternativeDto::name)
                .containsExactly("風雲児 新宿本店", "麺屋海神 新宿店");
        assertThat(stops.get(1).alternatives().get(0).lat()).isEqualTo(35.69);
        assertThat(stops.get(1).alternatives().get(0).lng()).isEqualTo(139.702);
    }

    @Test
    void 사이에_사라진_대안_식당은_건너뛴다() {
        Itinerary itinerary = itinerary(tripWithHotel(), List.of(second, third));
        ItineraryResponseDto dto = ItineraryResponseDto.from(itinerary, t -> List.of(), Map.of(3L, third));

        assertThat(dto.days().get(0).stops().get(1).alternatives())
                .extracting(ItineraryResponseDto.AlternativeDto::name)
                .containsExactly("麺屋海神 新宿店");
    }

    @Test
    void 일정의_대안_식당_id를_한꺼번에_모은다() {
        assertThat(ItineraryResponseDto.alternativeRestaurantIds(itinerary(tripWithHotel(), List.of(second, third))))
                .containsExactly(2L, 3L);
    }

    // ── 정기 휴일 (일정 날짜는 2026-10-20 화요일) ──

    private static Restaurant withHoliday(long id, String name, String holiday) {
        return Restaurant.builder().id(id).name(name).genre("洋食").ratingVal(3.8)
                .budgetLunch("～￥999").budgetDinner("￥1,000～￥1,999").address("도쿄").holiday(holiday)
                .location(GF.createPoint(new Coordinate(139.71, 35.70))).build();
    }

    private ItineraryResponseDto dtoWithDinner(Restaurant chosen, List<Restaurant> alternatives) {
        ScheduledDay day = new ScheduledDay(1, LocalDate.of(2026, 10, 20), TripZone.SHINJUKU,
                List.of(attractionStop(garden), dinnerStop(chosen, alternatives)), List.of(), List.of());
        Itinerary itinerary = ItineraryMapper.toEntity(tripWithHotel(), 1, 9.0, List.of(day),
                List.of(new DayWindow(LocalTime.of(16, 0), LocalTime.of(22, 0), false, true)), Map.of(),
                id -> garden, id -> chosen);
        Map<Long, Restaurant> byId = new java.util.HashMap<>();
        alternatives.forEach(r -> byId.put(r.getId(), r));
        return ItineraryResponseDto.from(itinerary, t -> List.of(), byId);
    }

    @Test
    void 방문하는_날이_정기_휴일이면_식당에_표시한다() {
        ItineraryResponseDto dto = dtoWithDinner(withHoliday(1, "화요일 휴무", "火曜日"), List.of());

        ItineraryResponseDto.RestaurantDto restaurant = dto.days().get(0).stops().get(1).restaurant();
        assertThat(restaurant.closedOnVisitDay()).isTrue();
        assertThat(restaurant.holiday()).isEqualTo("火曜日");
    }

    @Test
    void 다른_요일이_휴일이거나_문구가_애매하면_표시하지_않는다() {
        assertThat(dtoWithDinner(withHoliday(1, "일요일 휴무", "日曜日"), List.of())
                .days().get(0).stops().get(1).restaurant().closedOnVisitDay()).isFalse();
        assertThat(dtoWithDinner(withHoliday(1, "비정기", "不定休"), List.of())
                .days().get(0).stops().get(1).restaurant().closedOnVisitDay()).isFalse();
        assertThat(dtoWithDinner(withHoliday(1, "정보 없음", null), List.of())
                .days().get(0).stops().get(1).restaurant().closedOnVisitDay()).isFalse();
    }

    @Test
    void 대안에도_휴일_정보와_표시가_붙는다() {
        Restaurant closedAlternative = withHoliday(2, "화요일 휴무 대안", "月曜日、火曜日");
        Restaurant openAlternative = withHoliday(3, "무휴 대안", "無休");

        ItineraryResponseDto dto = dtoWithDinner(withHoliday(1, "선택", "日曜日"),
                List.of(closedAlternative, openAlternative));

        List<ItineraryResponseDto.AlternativeDto> alternatives = dto.days().get(0).stops().get(1).alternatives();
        assertThat(alternatives.get(0).closedOnVisitDay()).isTrue();
        assertThat(alternatives.get(0).holiday()).isEqualTo("月曜日、火曜日");
        assertThat(alternatives.get(1).closedOnVisitDay()).isFalse();
    }

}