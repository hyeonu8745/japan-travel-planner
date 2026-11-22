package com.example.japantravelplanner.itinerary.transit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 노선 이름·색 정리와 버스 정류장이 촘촘한 곳의 역 고르기 (11-22) */
class TransitGraphTest {

    @Test
    void 종별뿐인_노선_이름에는_회사_이름을_붙인다() {
        assertThat(TransitGraph.lineName("快速急行", "近畿日本鉄道;阪神電気鉄道")).isEqualTo("近鉄·阪神 快速急行");
        assertThat(TransitGraph.lineName("各駅停車", "小田急電鉄")).isEqualTo("小田急 各駅停車");
        assertThat(TransitGraph.lineName("快速急行", null)).isEqualTo("快速急行");
        assertThat(TransitGraph.lineName("Osaka Metro谷町線", "大阪市高速電気軌道")).isEqualTo("Osaka Metro谷町線");
    }

    @Test
    void 여러_값과_색을_화면에_쓰기_좋게() {
        assertThat(TransitGraph.joined("K;B")).isEqualTo("K·B");
        assertThat(TransitGraph.joined(" ; ")).isNull();
        assertThat(TransitGraph.colour("orangered")).isEqualTo("#FF4500");
        assertThat(TransitGraph.colour("DeepSkyBlue")).isEqualTo("#00BFFF");
        assertThat(TransitGraph.colour("#fc0")).isEqualTo("#FFCC00");
        assertThat(TransitGraph.colour("#522886")).isEqualTo("#522886");
        assertThat(TransitGraph.colour("rainbow")).isNull();
    }

    @Test
    void 도착_근처에_버스_정류장이_많아도_철도역을_후보에_남긴다() {
        // 출발 S 역에서 지하철로 R 역까지. 도착점 둘레 100~400m 에 버스 정류장 10곳(R 보다 가까움), R 은 500m
        List<TransitGraph.StopRow> stops = new ArrayList<>();
        stops.add(new TransitGraph.StopRow("s", "S", null, null, 35.000, 139.000));
        stops.add(new TransitGraph.StopRow("r", "R", null, null, 35.0045, 139.050));
        List<TransitGraph.RouteRow> routes = new ArrayList<>();
        routes.add(new TransitGraph.RouteRow("subway", "subway", null, "地下鉄", "市", "#123456", List.of("s", "r")));
        for (int i = 0; i < 10; i++) {
            String a = "b" + i, far = "f" + i;
            stops.add(new TransitGraph.StopRow(a, "停" + i, null, null, 35.0 + 0.0003 * (i + 1), 139.050));
            stops.add(new TransitGraph.StopRow(far, "遠" + i, null, null, 35.1 + 0.001 * i, 139.2));
            routes.add(new TransitGraph.RouteRow("bus" + i, "bus", null, "Bus " + i, "市", null, List.of(a, far)));
        }
        TransitGuide guide = new TransitGuide(new TransitGraph(stops, routes));

        TransitPlan plan = guide.plan(35.0, 139.0, 35.0, 139.050).orElseThrow();
        assertThat(plan.toStation()).isEqualTo("R");
        assertThat(plan.segments()).singleElement().extracting(TransitPlan.Segment::line).isEqualTo("地下鉄");
    }
}
