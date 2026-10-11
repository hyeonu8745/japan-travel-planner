package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하루에 두 지역 (10-11 사용자 결정: "여행 가서 못해도 두 지역은 하루에 도니까").
 * 예전에는 하루 = 지역 하나, 지역 중심 3km 안만 후보라 렌터카여도 한 동네 안에서만 돌았다.
 */
class DayZonePartnerTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private final CandidateProperties props = new CandidateProperties();
    private final DayZonePlanner planner = new DayZonePlanner(props);
    private static final LocalDate START = LocalDate.of(2026, 10, 13);

    private static ScoredAttraction spot(long id, String name, String types, TripZone zone,
                                         double lat, double lng, double score) {
        Attraction a = Attraction.builder().id(id).name(name).types(types)
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
        return new ScoredAttraction(a, zone, score);
    }

    // 실제 좌표
    private final ScoredAttraction sensoji = spot(1, "浅草寺", "buddhist_temple", TripZone.ASAKUSA_UENO, 35.7148, 139.7967, 4.60);
    private final ScoredAttraction uenoPark = spot(2, "上野恩賜公園", "park", TripZone.ASAKUSA_UENO, 35.7156, 139.7745, 4.50);
    private final ScoredAttraction ameyoko = spot(3, "アメ横商店街", "market", TripZone.ASAKUSA_UENO, 35.7101, 139.7746, 4.30);
    private final ScoredAttraction denkigai = spot(4, "秋葉原電気街", "tourist_attraction", TripZone.AKIHABARA_KANDA, 35.6998, 139.7713, 4.40);
    private final ScoredAttraction kanda = spot(5, "神田明神", "shinto_shrine", TripZone.AKIHABARA_KANDA, 35.7020, 139.7679, 4.40);
    private final ScoredAttraction gyoen = spot(6, "新宿御苑", "park", TripZone.SHINJUKU, 35.6852, 139.7100, 4.70);
    private final ScoredAttraction tocho = spot(7, "東京都庁", "observation_deck", TripZone.SHINJUKU, 35.6896, 139.6917, 4.60);
    private final List<ScoredAttraction> pool = List.of(sensoji, uenoPark, ameyoko, denkigai, kanda, gyoen, tocho);

    @Test
    void 이동_범위가_있으면_가까운_이웃_지역을_함께_돌고_두_지역_관광지가_후보가_된다() {
        // 숙소 아사쿠사, 1일 여행: 중심은 아사쿠사·우에노, 3km 남짓 떨어진 아키하바라·간다를 함께 돈다
        List<DayZone> days = planner.plan(List.of(sensoji, uenoPark, ameyoko, denkigai, kanda), START, 1, 4,
                List.of(), List.of(), 35.7110, 139.7960, false, planner.rangeMeters(false, false));

        DayZone day = days.get(0);
        assertThat(day.zone()).isEqualTo(TripZone.ASAKUSA_UENO);
        assertThat(day.partner()).isEqualTo(TripZone.AKIHABARA_KANDA);
        assertThat(day.candidates()).contains(denkigai, kanda, sensoji);
        assertThat(day.covers(TripZone.AKIHABARA_KANDA)).isTrue();
        assertThat(day.covers(TripZone.SHINJUKU)).isFalse();
    }

    @Test
    void 이동_범위가_0이면_예전처럼_한_지역만() {
        List<DayZone> days = planner.plan(List.of(sensoji, uenoPark, ameyoko, denkigai, kanda), START, 1, 4,
                List.of(), List.of(), 35.7110, 139.7960, false);

        assertThat(days.get(0).partner()).isNull();
        assertThat(days.get(0).candidates()).containsOnly(sensoji, uenoPark, ameyoko);
    }

    @Test
    void 가까운_곳_위주_7km면_아사쿠사에서_신주쿠는_함께_돌지_않는다() {
        // 아사쿠사·우에노 중심 ↔ 신주쿠 중심 약 7.9km. 신주쿠 관광지 점수가 더 높아도 범위 밖
        var byZone = java.util.Map.of(
                TripZone.ASAKUSA_UENO, List.of(sensoji, uenoPark),
                TripZone.SHINJUKU, List.of(gyoen, tocho));

        TripZone compact = planner.choosePartner(TripZone.ASAKUSA_UENO, byZone, java.util.Set.of(),
                java.util.Set.of(TripZone.ASAKUSA_UENO), new java.util.EnumMap<>(TripZone.class), 4, List.of(),
                planner.rangeMeters(true, false));
        TripZone transit = planner.choosePartner(TripZone.ASAKUSA_UENO, byZone, java.util.Set.of(),
                java.util.Set.of(TripZone.ASAKUSA_UENO), new java.util.EnumMap<>(TripZone.class), 4, List.of(),
                planner.rangeMeters(false, false));

        assertThat(compact).isNull();
        assertThat(transit).isEqualTo(TripZone.SHINJUKU);
    }

    @Test
    void 다른_날의_중심_지역보다_아직_안_간_지역을_함께_돈다() {
        // 2일 여행: 아사쿠사·우에노, 신주쿠가 중심. 아사쿠사 날의 짝은 신주쿠(점수 높음)가 아니라 아키하바라
        List<DayZone> days = planner.plan(pool, START, 2, 4, List.of(), List.of(), 35.7110, 139.7960,
                false, planner.rangeMeters(false, false));

        DayZone asakusa = days.stream().filter(d -> d.zone() == TripZone.ASAKUSA_UENO).findFirst().orElseThrow();
        assertThat(asakusa.partner()).isEqualTo(TripZone.AKIHABARA_KANDA);
    }

    @Test
    void 같은_관광지는_두_날에_나오지_않는다() {
        List<DayZone> days = planner.plan(pool, START, 2, 2, List.of(), List.of(), 35.7110, 139.7960,
                false, planner.rangeMeters(false, true));

        List<Long> firstTwo = days.stream()
                .flatMap(d -> d.candidates().stream().limit(2))
                .map(c -> c.attraction().getId()).toList();
        assertThat(firstTwo).doesNotHaveDuplicates();
    }

    @Test
    void 이동수단별_하루_범위() {
        assertThat(planner.rangeMeters(true, true)).isEqualTo(7_000);    // 가까운 곳 위주가 먼저
        assertThat(planner.rangeMeters(false, false)).isEqualTo(10_000);
        assertThat(planner.rangeMeters(false, true)).isEqualTo(12_000);
    }

    @Test
    void 야경_장소는_함께_도는_지역에서도_고른다() {
        ScoredAttraction tower = spot(8, "東京タワー", "observation_deck", TripZone.ROPPONGI_TOWER, 35.6586, 139.7454, 4.6);
        DayZone day = new DayZone(1, START, TripZone.GINZA_TOKYO, List.of(), TripZone.ROPPONGI_TOWER);
        var window = com.example.japantravelplanner.itinerary.planner.DayWindow.of(false, false, null, null, true);

        var nights = NightSpotPlanner.assign(List.of(day), List.of(tower), List.of(window));

        assertThat(nights).containsEntry(1, tower);
    }
}
