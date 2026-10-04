package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DaySchedulerTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final TravelPoint HOTEL = new TravelPoint("숙소", 35.6951, 139.7020);

    private final DayScheduler scheduler = new DayScheduler(new TravelTimeService(new TravelProperties(), null));
    private final TravelOptions transit = new TravelOptions(LegMode.TRANSIT, 20);

    // ── 테스트 자료 ──

    private static ScoredAttraction spot(long id, String name, String types, double lat, double lng, double score) {
        Attraction a = Attraction.builder().id(id).name(name).types(types)
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
        return new ScoredAttraction(a, TripZone.SHINJUKU, score);
    }

    private static PlannedMeal meal(Meal type, String name, double lat, double lng) {
        Restaurant r = Restaurant.builder().id(type == Meal.LUNCH ? 100L : 200L).name(name).genre("ラーメン")
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
        return new PlannedMeal(type, new TravelPoint("기준", lat, lng),
                new RestaurantCandidate(r, 100, 4.0, false), List.of(), false);
    }

    private static DayRoute route(List<ScoredAttraction> morning, List<ScoredAttraction> afternoon,
                                  ScoredAttraction night) {
        return route(morning, afternoon, night, List.of());
    }

    private static DayRoute route(List<ScoredAttraction> morning, List<ScoredAttraction> afternoon,
                                  ScoredAttraction night, List<ScoredAttraction> spares) {
        DayZone day = new DayZone(1, LocalDate.of(2026, 10, 20), TripZone.SHINJUKU, List.of());
        return new DayRoute(day, morning, afternoon, night, spares, List.of(), null, 0);
    }

    private static DayWindow window(String start, String end, boolean lunch, boolean dinner) {
        return new DayWindow(LocalTime.parse(start), LocalTime.parse(end), lunch, dinner);
    }

    private final PlannedMeal lunch = meal(Meal.LUNCH, "점심집", 35.6935, 139.7030);
    private final PlannedMeal dinner = meal(Meal.DINNER, "저녁집", 35.6900, 139.6960);

    // 같은 신주쿠 안의 가까운 곳들 (기타 종류 = 체류 40분)
    private final ScoredAttraction a1 = spot(1, "관광지1", "tourist_attraction", 35.6926, 139.7008, 4.8);
    private final ScoredAttraction a2 = spot(2, "관광지2", "tourist_attraction", 35.6938, 139.7046, 4.6);
    private final ScoredAttraction a3 = spot(3, "관광지3", "tourist_attraction", 35.6930, 139.6995, 4.4);
    private final ScoredAttraction a4 = spot(4, "관광지4", "tourist_attraction", 35.6941, 139.7048, 4.2);
    private final ScoredAttraction deck = spot(5, "전망대", "observation_deck", 35.6895, 139.6917, 4.9);

    private static List<String> names(ScheduledDay day) {
        return day.stops().stream().map(ScheduledStop::name).toList();
    }

    private static ScheduledStop find(ScheduledDay day, StopType type) {
        return day.stops().stream().filter(s -> s.type() == type).findFirst().orElseThrow();
    }

    // ── 보통의 하루 ──

    @Test
    void 보통의_하루는_순서대로_시각이_붙고_아무것도_빠지지_않는다() {
        ScheduledDay day = scheduler.schedule(route(List.of(a1, a2), List.of(a3, a4), deck),
                new DayMeals(lunch, dinner), window("09:30", "22:00", true, true),
                HOTEL, transit, 1.0);

        assertThat(names(day)).containsExactly("관광지1", "관광지2", "점심집", "관광지3", "관광지4", "저녁집", "전망대");
        assertThat(day.dropped()).isEmpty();
        assertThat(day.warnings()).isEmpty();

        // 각 항목은 앞 항목이 끝난 뒤에 시작하고, 체류 시간만큼 머문다
        for (int i = 0; i < day.stops().size(); i++) {
            ScheduledStop s = day.stops().get(i);
            assertThat(s.departure()).isEqualTo(s.arrival().plusMinutes(s.stayMinutes()));
            if (i > 0) assertThat(s.arrival()).isAfter(day.stops().get(i - 1).departure());
        }
        assertThat(day.stops().get(0).arrival()).isAfter(LocalTime.of(9, 30));    // 숙소에서 이동 + 10분
        assertThat(find(day, StopType.LUNCH).arrival()).isBetween(LocalTime.of(11, 30), LocalTime.of(13, 30));
        assertThat(find(day, StopType.DINNER).arrival()).isBetween(LocalTime.of(18, 0), LocalTime.of(20, 0));
        assertThat(day.stops().get(day.stops().size() - 1).departure()).isBeforeOrEqualTo(LocalTime.of(22, 0));
    }

    // ── 식사 시간 맞추기 ──

    @Test
    void 점심_시간보다_일찍_도착하면_11시30분까지_기다린다() {
        ScheduledDay day = scheduler.schedule(route(List.of(a1), List.of(), null),
                new DayMeals(lunch, null), window("09:30", "21:00", true, false),
                HOTEL, transit, 1.0);

        ScheduledStop l = find(day, StopType.LUNCH);
        assertThat(l.arrival()).isEqualTo(LocalTime.of(11, 30));
        assertThat(l.waitMinutes()).isGreaterThan(0);
        assertThat(l.departure()).isEqualTo(LocalTime.of(12, 30));   // 점심 60분
    }

    @Test
    void 저녁도_18시_전에_도착하면_기다린다() {
        ScheduledDay day = scheduler.schedule(route(List.of(a1), List.of(a2), null),
                new DayMeals(lunch, dinner), window("09:30", "21:00", true, true),
                HOTEL, transit, 1.0);

        ScheduledStop d = find(day, StopType.DINNER);
        assertThat(d.arrival()).isEqualTo(LocalTime.of(18, 0));
        assertThat(d.waitMinutes()).isGreaterThan(0);
    }

    @Test
    void 점심이_늦어지면_점수_낮은_오전_관광지를_오후로_옮긴다() {
        // 박물관 3곳(각 90분)을 점심 전에 넣으면 점심이 14시를 넘긴다
        ScoredAttraction m1 = spot(11, "박물관1", "museum", 35.6926, 139.7008, 4.8);
        ScoredAttraction m2 = spot(12, "박물관2", "museum", 35.6938, 139.7046, 4.6);
        ScoredAttraction m3 = spot(13, "박물관3", "museum", 35.6930, 139.6995, 4.4);

        ScheduledDay day = scheduler.schedule(route(List.of(m1, m2, m3), List.of(), null),
                new DayMeals(lunch, null), window("09:30", "21:00", true, false),
                HOTEL, transit, 1.0);

        // 점수가 가장 낮은 박물관3이 점심 뒤로 이동, 아무것도 빠지지 않음
        assertThat(names(day)).containsExactly("박물관1", "박물관2", "점심집", "박물관3");
        assertThat(find(day, StopType.LUNCH).arrival()).isBeforeOrEqualTo(LocalTime.of(13, 30));
        assertThat(day.dropped()).isEmpty();
        assertThat(day.warnings()).isEmpty();
    }

    // ── 시간이 모자랄 때 빼기 ──

    @Test
    void 마지막_날_16시에_끝나야_하면_오후의_점수_낮은_곳부터_뺀다() {
        ScoredAttraction m1 = spot(11, "박물관1", "museum", 35.6926, 139.7008, 4.8);
        ScoredAttraction m2 = spot(12, "박물관2", "museum", 35.6938, 139.7046, 4.6);
        ScoredAttraction m3 = spot(13, "박물관3", "museum", 35.6930, 139.6995, 4.4);
        ScoredAttraction m4 = spot(14, "박물관4", "museum", 35.6941, 139.7048, 4.2);

        ScheduledDay day = scheduler.schedule(route(List.of(m1, m2), List.of(m3, m4), null),
                new DayMeals(lunch, null), window("09:30", "16:00", true, false),
                HOTEL, transit, 1.0);

        // 오후 중 점수가 가장 낮은 박물관4가 먼저 빠진다
        assertThat(day.dropped()).extracting(DroppedStop::name).startsWith("박물관4");
        assertThat(day.dropped()).extracting(DroppedStop::reason).containsOnly(DropReason.TIME_SHORTAGE);
        assertThat(names(day)).doesNotContain("박물관4");
        assertThat(day.stops().get(day.stops().size() - 1).departure()).isBeforeOrEqualTo(LocalTime.of(16, 0));
        assertThat(day.warnings()).isEmpty();
    }

    @Test
    void 첫날_16시_시작이면_저녁과_야경이_들어가도록_관광지를_뺀다() {
        ScheduledDay day = scheduler.schedule(route(List.of(a1, a2), List.of(a3, a4), deck),
                new DayMeals(null, dinner), window("16:00", "22:00", false, true),
                HOTEL, transit, 1.0);

        assertThat(find(day, StopType.DINNER).arrival()).isBetween(LocalTime.of(18, 0), LocalTime.of(20, 0));
        assertThat(day.stops().get(day.stops().size() - 1).name()).isEqualTo("전망대");   // 야경이 마지막
        assertThat(day.stops().get(day.stops().size() - 1).departure()).isBeforeOrEqualTo(LocalTime.of(22, 0));
        assertThat(day.dropped()).isNotEmpty();
        assertThat(day.dropped().get(0).name()).isEqualTo("관광지4");   // 점수 가장 낮은 곳
        assertThat(day.warnings()).isEmpty();
    }

    @Test
    void 관광지를_빼도_나아지지_않으면_야경을_뺀다() {
        // 저녁이 늦게 끝나는 하루: 야경 때문에 22시를 넘기지만 관광지는 이미 저녁 전에 끝나 있다
        ScheduledDay day = scheduler.schedule(route(List.of(a1), List.of(), deck),
                new DayMeals(null, dinner), window("09:30", "20:30", false, true),
                HOTEL, transit, 1.0);

        assertThat(names(day)).containsExactly("관광지1", "저녁집");
        assertThat(day.dropped()).extracting(DroppedStop::name).containsExactly("전망대");
    }

    // ── 끼니·시간대 예외 ──

    @Test
    void 저녁이_없으면_야경도_뺀다() {
        ScheduledDay day = scheduler.schedule(route(List.of(a1, a2), List.of(), deck),
                new DayMeals(lunch, null), window("09:30", "16:00", true, false),
                HOTEL, transit, 1.0);

        assertThat(names(day)).doesNotContain("전망대");
        assertThat(day.dropped()).extracting(DroppedStop::name).containsExactly("전망대");
        assertThat(day.dropped().get(0).reason()).isEqualTo(DropReason.NIGHT_WITHOUT_DINNER);
    }

    @Test
    void 식당_후보가_없으면_안내하고_그_끼니는_건너뛴다() {
        PlannedMeal none = PlannedMeal.notFound(Meal.LUNCH, new TravelPoint("기준", 35.69, 139.70));
        ScheduledDay day = scheduler.schedule(route(List.of(a1), List.of(a2), null),
                new DayMeals(none, null), window("09:30", "21:00", true, false),
                HOTEL, transit, 1.0);

        assertThat(names(day)).containsExactly("관광지1", "관광지2");
        assertThat(day.warnings()).hasSize(1);
        assertThat(day.warnings().get(0)).contains("점심");
    }

    @Test
    void 일정을_넣을_수_없는_날은_빈_일정과_안내() {
        ScheduledDay day = scheduler.schedule(route(List.of(a1), List.of(a2), null),
                new DayMeals(null, null), new DayWindow(null, null, false, false),
                HOTEL, transit, 1.0);

        assertThat(day.stops()).isEmpty();
        assertThat(day.warnings()).hasSize(1);
    }

    // ── 속도 배율 ──

    @Test
    void 여유로운_속도는_체류_시간이_길어진다() {
        ScheduledDay normal = scheduler.schedule(route(List.of(a1), List.of(), null),
                new DayMeals(null, null), window("09:30", "21:00", false, false), HOTEL, transit, 1.0);
        ScheduledDay relaxed = scheduler.schedule(route(List.of(a1), List.of(), null),
                new DayMeals(null, null), window("09:30", "21:00", false, false), HOTEL, transit, 1.3);

        assertThat(normal.stops().get(0).stayMinutes()).isEqualTo(40);
        assertThat(relaxed.stops().get(0).stayMinutes()).isEqualTo(50);   // 52 → 50
    }

    @Test
    void 첫_항목은_숙소에서의_이동이다() {
        ScheduledDay day = scheduler.schedule(route(List.of(a1), List.of(), null),
                new DayMeals(null, null), window("09:30", "21:00", false, false), HOTEL, transit, 1.0);

        ScheduledStop first = day.stops().get(0);
        assertThat(first.travelIn().mode()).isEqualTo(LegMode.WALK);
        // 도착 = 출발 시각 + 이동 + 10분 여유
        assertThat(first.arrival()).isEqualTo(LocalTime.of(9, 30).plusMinutes(first.travelIn().minutes() + 10));
    }

    // ── 예비 관광지로 채우기 ──

    private final ScoredAttraction spare1 = spot(21, "예비1", "tourist_attraction", 35.6945, 139.7040, 4.1);
    private final ScoredAttraction spare2 = spot(22, "예비2", "tourist_attraction", 35.6948, 139.7035, 4.0);

    @Test
    void 저녁_전에_한참_비면_예비_관광지를_끼워_넣는다() {
        // 관광지 4곳(각 40분)이 14시대에 끝나서 저녁(18시)까지 3시간 넘게 빈다
        DayMeals meals = new DayMeals(lunch, dinner);
        DayWindow window = window("09:30", "22:00", true, true);

        ScheduledDay without = scheduler.schedule(route(List.of(a1, a2), List.of(a3, a4), deck),
                meals, window, HOTEL, transit, 1.0);
        ScheduledDay filled = scheduler.schedule(route(List.of(a1, a2), List.of(a3, a4), deck, List.of(spare1, spare2)),
                meals, window, HOTEL, transit, 1.0);

        assertThat(names(without)).doesNotContain("예비1", "예비2");
        assertThat(names(filled)).contains("예비1", "예비2");
        assertThat(find(filled, StopType.DINNER).waitMinutes())
                .isLessThan(find(without, StopType.DINNER).waitMinutes());
        assertThat(find(filled, StopType.DINNER).arrival()).isBetween(LocalTime.of(18, 0), LocalTime.of(20, 0));
        assertThat(filled.dropped()).isEmpty();
        assertThat(filled.warnings()).isEmpty();
    }

    @Test
    void 대기가_예비_하나로_충분히_줄면_거기서_멈춘다() {
        // 오후 박물관 2곳이 16시쯤 끝나 저녁 대기가 1시간 40분 → 체류 120분짜리 예비 하나면 충분
        ScoredAttraction m3 = spot(13, "박물관3", "museum", 35.6930, 139.6995, 4.4);
        ScoredAttraction m4 = spot(14, "박물관4", "museum", 35.6941, 139.7048, 4.2);
        ScoredAttraction park = spot(23, "큰예비", "amusement_park", 35.6945, 139.7040, 4.1);

        ScheduledDay day = scheduler.schedule(
                route(List.of(a1, a2), List.of(m3, m4), null, List.of(park, spare2)),
                new DayMeals(lunch, dinner), window("09:30", "21:00", true, true),
                HOTEL, transit, 1.0);

        assertThat(names(day)).contains("큰예비");
        assertThat(find(day, StopType.DINNER).waitMinutes()).isLessThan(DayScheduler.FILL_IDLE_MINUTES);
        assertThat(find(day, StopType.DINNER).arrival()).isBeforeOrEqualTo(LocalTime.of(20, 0));
        assertThat(names(day)).doesNotContain("예비2");
    }

    @Test
    void 대기가_짧으면_예비를_쓰지_않는다() {
        // 박물관(각 90분)이라 오후가 17시 가까이 끝남 → 저녁 대기 1시간 미만
        ScoredAttraction m1 = spot(11, "박물관1", "museum", 35.6926, 139.7008, 4.8);
        ScoredAttraction m2 = spot(12, "박물관2", "museum", 35.6938, 139.7046, 4.6);
        ScoredAttraction m3 = spot(13, "박물관3", "museum", 35.6930, 139.6995, 4.4);
        ScoredAttraction m4 = spot(14, "박물관4", "museum", 35.6941, 139.7048, 4.2);

        ScheduledDay day = scheduler.schedule(route(List.of(m1, m2), List.of(m3, m4), null, List.of(spare1)),
                new DayMeals(lunch, dinner), window("09:30", "21:00", true, true),
                HOTEL, transit, 1.0);

        assertThat(find(day, StopType.DINNER).waitMinutes()).isLessThan(DayScheduler.FILL_IDLE_MINUTES);
        assertThat(names(day)).doesNotContain("예비1");
    }

    @Test
    void 시간이_모자라_뺀_날은_예비로_다시_채우지_않는다() {
        ScoredAttraction m1 = spot(11, "박물관1", "museum", 35.6926, 139.7008, 4.8);
        ScoredAttraction m2 = spot(12, "박물관2", "museum", 35.6938, 139.7046, 4.6);
        ScoredAttraction m3 = spot(13, "박물관3", "museum", 35.6930, 139.6995, 4.4);
        ScoredAttraction m4 = spot(14, "박물관4", "museum", 35.6941, 139.7048, 4.2);

        ScheduledDay day = scheduler.schedule(route(List.of(m1, m2), List.of(m3, m4), null, List.of(spare1)),
                new DayMeals(lunch, null), window("09:30", "16:00", true, false),
                HOTEL, transit, 1.0);

        assertThat(day.dropped()).isNotEmpty();
        assertThat(names(day)).doesNotContain("예비1");
    }

    @Test
    void 멀리_돌아가야_하는_예비는_채우지_않고_가까운_예비를_쓴다() {
        // 실제 DAY2: 점심 뒤에 롯폰기 쪽으로 되돌아가는 なだれ坂가 들어가 이동이 30분 넘게 늘었다
        // 숙소에서 서쪽으로 약 3km 떨어진 예비 (끼우면 오가는 데 20분 넘게 더 걸린다)
        ScoredAttraction far = spot(24, "먼예비", "tourist_attraction", 35.6951, 139.7380, 4.5);

        ScheduledDay day = scheduler.schedule(
                route(List.of(a1, a2), List.of(a3, a4), deck, List.of(far, spare1)),
                new DayMeals(lunch, dinner), window("09:30", "22:00", true, true),
                HOTEL, transit, 1.0);

        assertThat(names(day)).doesNotContain("먼예비");
        assertThat(names(day)).contains("예비1");   // 먼 예비를 건너뛰고 다음 예비를 본다
    }

    @Test
    void 가까운_예비는_이동_상한_안에서_채운다() {
        ScheduledDay noFill = scheduler.schedule(route(List.of(a1, a2), List.of(a3, a4), deck),
                new DayMeals(lunch, dinner), window("09:30", "22:00", true, true), HOTEL, transit, 1.0);
        ScheduledDay filled = scheduler.schedule(route(List.of(a1, a2), List.of(a3, a4), deck, List.of(spare1)),
                new DayMeals(lunch, dinner), window("09:30", "22:00", true, true), HOTEL, transit, 1.0);

        int before = noFill.stops().stream().mapToInt(s -> s.travelIn().minutes()).sum();
        int after = filled.stops().stream().mapToInt(s -> s.travelIn().minutes()).sum();

        assertThat(names(filled)).contains("예비1");
        assertThat(after - before).isLessThanOrEqualTo(DayScheduler.MAX_FILL_DETOUR_MINUTES);
    }

}