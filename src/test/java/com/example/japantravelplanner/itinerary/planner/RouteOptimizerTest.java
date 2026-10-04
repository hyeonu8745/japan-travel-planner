package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.travel.*;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.trip.entity.TravelPace;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOptimizerTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final RouteOptimizer optimizer =
            new RouteOptimizer(new TravelTimeService(new TravelProperties(), null));
    private final TravelOptions transit = new TravelOptions(LegMode.TRANSIT, 20);

    // ── bestOrder: 직선 위의 점으로 만든 표 (이동 시간 = 위치 차이) ──

    /** positions[0]은 출발지. 나머지는 관광지 위치 */
    private static TravelMatrix line(int... positions) {
        int n = positions.length;
        TravelLeg[][] legs = new TravelLeg[n][n];
        List<TravelPoint> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new TravelPoint("p" + i, 0, 0));
            for (int j = 0; j < n; j++) {
                if (i != j) legs[i][j] = new TravelLeg(Math.abs(positions[i] - positions[j]), 0, LegMode.WALK, false);
            }
        }
        return new TravelMatrix(points, legs);
    }

    @Test
    void 끝점이_정해지면_한_방향으로_쭉_간다() {
        // 출발 0, 관광지 30·10·20, 끝(야경) 40 → 10, 20, 30 순서 (1번=30, 2번=10, 3번=20)
        TravelMatrix m = line(0, 30, 10, 20, 40);
        int[] order = RouteOptimizer.bestOrder(m, 3, 4);
        assertThat(order).containsExactly(2, 3, 1);
        assertThat(RouteOptimizer.pathMinutes(m, order, 4)).isEqualTo(40);
    }

    @Test
    void 숙소로_돌아오는_경우도_왔다갔다_하지_않는다() {
        // 출발 0, 관광지 -10·20·10 → 한쪽을 다 보고 반대쪽으로 (총 60분)
        TravelMatrix m = line(0, -10, 20, 10);
        int[] order = RouteOptimizer.bestOrder(m, 3, 0);
        assertThat(RouteOptimizer.pathMinutes(m, order, 0)).isEqualTo(60);
    }

    @Test
    void 관광지가_없으면_빈_순서() {
        assertThat(RouteOptimizer.bestOrder(line(0), 0, 0)).isEmpty();
    }

    // ── optimize: 실제 좌표 ──

    private static ScoredAttraction spot(long id, String name, String types, double lat, double lng, double score) {
        Attraction a = Attraction.builder()
                .id(id).name(name).types(types).rating(4.5).reviewCnt(1000)
                .location(GF.createPoint(new Coordinate(lng, lat)))
                .build();
        return new ScoredAttraction(a, TripZone.SHINJUKU, score);
    }

    private static DayZone day(List<ScoredAttraction> candidates) {
        return new DayZone(1, LocalDate.of(2026, 10, 20), TripZone.SHINJUKU, candidates);
    }

    private final TravelPoint hotel = new TravelPoint("숙소(가부키초)", 35.6951588, 139.7020539);

    // 신주쿠 실제 좌표 (점수순)
    private final ScoredAttraction tocho = spot(1, "東京都庁 展望室", "observation_deck,tourist_attraction",
            35.6894807, 139.6916863, 5.0);
    private final ScoredAttraction gyoen = spot(2, "新宿御苑", "park,tourist_attraction",
            35.6851763, 139.7100517, 4.8);
    private final ScoredAttraction hanazono = spot(3, "花園神社", "shinto_shrine,tourist_attraction",
            35.6938, 139.7046, 4.6);
    private final ScoredAttraction omoide = spot(4, "思い出横丁", "tourist_attraction",
            35.6930, 139.6995, 4.5);
    private final ScoredAttraction goldengai = spot(5, "新宿ゴールデン街", "tourist_attraction",
            35.6941, 139.7048, 4.4);
    private final ScoredAttraction spare = spot(6, "예비 관광지", "museum",
            35.6900, 139.7000, 4.0);

    @Test
    void 오전_오후_개수대로_나누고_남은_후보는_예비로() {
        DayRoute route = optimizer.optimize(day(List.of(gyoen, hanazono, omoide, goldengai, spare)),
                hotel, TravelPace.NORMAL, transit, false);

        assertThat(route.morning()).hasSize(2);
        assertThat(route.afternoon()).hasSize(2);
        assertThat(route.spares()).containsExactly(spare);
        assertThat(route.legs()).hasSize(4);
        assertThat(route.night()).isNull();
        assertThat(route.endLeg()).isNotNull();     // 숙소로 돌아오는 구간
    }

    @Test
    void 야경_관심사면_전망대를_저녁_후로_빼고_예비가_낮을_채운다() {
        DayRoute route = optimizer.optimize(day(List.of(tocho, gyoen, hanazono, omoide, goldengai, spare)),
                hotel, TravelPace.NORMAL, transit, true);

        assertThat(route.night()).isEqualTo(tocho);
        assertThat(route.daytime()).containsExactlyInAnyOrder(gyoen, hanazono, omoide, goldengai);
        assertThat(route.daytime()).doesNotContain(tocho);
        assertThat(route.spares()).containsExactly(spare);
    }

    @Test
    void 야경_관심사가_없으면_전망대도_낮_관광지다() {
        DayRoute route = optimizer.optimize(day(List.of(tocho, gyoen, hanazono, omoide, goldengai, spare)),
                hotel, TravelPace.NORMAL, transit, false);

        assertThat(route.night()).isNull();
        assertThat(route.daytime()).contains(tocho);
    }

    @Test
    void 전망대가_없으면_야경_장소는_비운다() {
        DayRoute route = optimizer.optimize(day(List.of(gyoen, hanazono, omoide, goldengai)),
                hotel, TravelPace.NORMAL, transit, true);

        assertThat(route.night()).isNull();
        assertThat(route.daytime()).hasSize(4);
    }

    @Test
    void 후보가_적으면_있는_만큼만() {
        DayRoute route = optimizer.optimize(day(List.of(gyoen, hanazono)),
                hotel, TravelPace.FULL, transit, false);

        assertThat(route.morning()).hasSize(2);
        assertThat(route.afternoon()).isEmpty();
    }

    @Test
    void 후보가_없으면_빈_하루() {
        DayRoute route = optimizer.optimize(day(List.of()), hotel, TravelPace.NORMAL, transit, true);
        assertThat(route.daytime()).isEmpty();
        assertThat(route.travelMinutes()).isZero();
    }
}