package com.example.japantravelplanner.itinerary.transit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 구간마다 대강의 대중교통 경로 (11-22). 작은 가짜 노선망으로 */
class TransitGuideTest {

    // 동서로 놓인 역 A(0) — B — C(환승) — D, C 에서 남북으로 C — E — F. 역 사이 약 1.1km (경도 0.012)
    private static TransitGraph.StopRow stop(String id, String name, double lat, double lng) {
        return new TransitGraph.StopRow(id, name, null, name.equals("C駅") ? "C역" : null, lat, lng);
    }

    private static final List<TransitGraph.StopRow> STOPS = List.of(
            stop("a", "A", 35.000, 139.000), stop("b", "B", 35.000, 139.012),
            stop("c1", "C駅", 35.000, 139.024), stop("c2", "C", 35.0005, 139.0242),   // 같은 역의 다른 노선 정차 지점
            stop("d", "D", 35.000, 139.036),
            stop("e", "E", 34.990, 139.0242), stop("f", "F", 34.980, 139.0242),
            stop("x", "A", 35.200, 139.000));   // 이름만 같은 먼 역

    private static TransitGraph.RouteRow route(String id, String mode, String line, String colour, String... stops) {
        return new TransitGraph.RouteRow(id, mode, null, line, "会社", colour, List.of(stops));
    }

    private final TransitGraph graph = new TransitGraph(STOPS, List.of(
            route("east", "subway", "東西線", "#009BBF", "a", "b", "c1", "d"),
            route("south", "subway", "南北線", "#00AC9B", "c2", "e", "f"),
            route("express", "train", "特急はやて", null, "a", "f")));
    private final TransitGuide guide = new TransitGuide(graph);

    @Test
    void 같은_이름이고_가까운_정차_지점은_한_역으로_묶고_먼_곳은_따로() {
        assertThat(graph.stationCount()).isEqualTo(7);   // C駅·C 는 하나, 먼 A 는 따로
        assertThat(TransitGraph.key("新宿駅")).isEqualTo(TransitGraph.key("新宿"));
        assertThat(TransitGraph.display("佐保小学校 (Saho)")).isEqualTo("佐保小学校");
    }

    @Test
    void 한_노선으로_가면_직통() {
        TransitPlan plan = guide.plan(35.0002, 139.0001, 35.0001, 139.0359).orElseThrow();

        assertThat(plan.fromStation()).isEqualTo("A");
        assertThat(plan.toStation()).isEqualTo("D");
        assertThat(plan.segments()).singleElement().satisfies(s -> {
            assertThat(s.line()).isEqualTo("東西線");
            assertThat(s.colour()).isEqualTo("#009BBF");
            assertThat(s.stops()).isEqualTo(3);
        });
        assertThat(plan.transferStation()).isNull();
        assertThat(plan.walkToMeters()).isLessThan(50);
    }

    @Test
    void 이어지지_않으면_같이_서는_역에서_한_번_갈아타고_특급은_쓰지_않는다() {
        TransitPlan plan = guide.plan(35.0, 139.0, 34.980, 139.0242).orElseThrow();

        assertThat(plan.segments()).extracting(TransitPlan.Segment::line).containsExactly("東西線", "南北線");
        assertThat(plan.transferStation()).isEqualTo("C駅");   // 먼저 들어온 정차 지점 이름
        assertThat(plan.segments().get(0).toKo()).isEqualTo("C역");
        assertThat(plan.segments().get(1).stops()).isEqualTo(2);
    }

    @Test
    void 가까운_구간이나_근처에_역이_없으면_찾지_않는다() {
        assertThat(guide.plan(35.0, 139.0, 35.0, 139.004)).isEmpty();     // 약 360m: 걷는 구간
        assertThat(guide.plan(35.0, 139.0, 35.5, 139.5)).isEmpty();       // 도착 근처에 역 없음
        assertThat(new TransitGuide(TransitGraph.EMPTY).plan(35.0, 139.0, 35.0, 139.036)).isEmpty();
    }

    @Test
    void 특급과_신칸센_열차_이름은_시내_구간에_쓰지_않는다() {
        assertThat(TransitGuide.usable(new TransitGraph.Line("1", "train", null, "特急はるか", null, null, new int[]{0, 1}))).isFalse();
        assertThat(TransitGuide.usable(new TransitGraph.Line("2", "train", null, "のぞみ", null, null, new int[]{0, 1}))).isFalse();
        assertThat(TransitGuide.usable(new TransitGraph.Line("3", "train", null, "ソニック", null, null, new int[]{0, 1}))).isFalse();
        assertThat(TransitGuide.usable(new TransitGraph.Line("4", "subway", null, "福岡市地下鉄空港線", null, null, new int[]{0, 1}))).isTrue();
        assertThat(TransitGuide.usable(new TransitGraph.Line("5", "train", null, "JR鹿児島本線", null, null, new int[]{0, 1}))).isTrue();
    }

    @Test
    void 가까운_역이_없으면_넓혀_보되_다_걷는_것보다_빠를_때만() {
        // A 에서 서쪽으로 약 1.1km 떨어진 곳 → D: 800m 안에는 역이 없지만 A 까지 걸어서 타면 훨씬 빠르다
        TransitPlan plan = guide.plan(35.0, 138.988, 35.0, 139.036).orElseThrow();
        assertThat(plan.fromStation()).isEqualTo("A");
        assertThat(plan.walkToMeters()).isGreaterThan(800);

        // 역 사이 거리보다 걷는 거리가 더 긴 짧은 구간은 그냥 걷는다: A 서쪽 1.4km → A 남쪽 1.4km
        assertThat(guide.plan(35.0, 138.9846, 34.9874, 139.0)).isEmpty();
    }

    @Test
    void 응답_JSON에_갈아타는_역도_나간다() {
        TransitPlan plan = guide.plan(35.0, 139.0, 34.980, 139.0242).orElseThrow();
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(plan);

        assertThat(json).contains("\"transferStation\":\"C駅\"").contains("\"segments\":[").contains("\"colour\":\"#009BBF\"");
    }

    @Test
    void 구간마다_지나는_역의_좌표를_순서대로_준다() {
        TransitPlan plan = guide.plan(35.0, 139.0, 34.980, 139.0242).orElseThrow();

        assertThat(plan.segments().get(0).path()).extracting(TransitPlan.Point::name).containsExactly("A", "B", "C駅");
        assertThat(plan.segments().get(1).path()).extracting(TransitPlan.Point::name).containsExactly("C駅", "E", "F");
        assertThat(plan.segments().get(1).path().get(2)).satisfies(p -> {
            assertThat(p.lat()).isEqualTo(34.98);
            assertThat(p.lng()).isEqualTo(139.0242);
        });
        assertThat(plan.segments().get(0).path()).hasSize(plan.segments().get(0).stops() + 1);
    }
}
