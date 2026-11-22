package com.example.japantravelplanner.itinerary.transit;

import com.example.japantravelplanner.global.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 구간마다 대강의 대중교통 경로 찾기 (11-22 사용자 결정: "대강 보여 주는 느낌", 시간 계산은 지금 추정 그대로).
 *
 * - 앞 장소·다음 장소에서 걸어서 800m 안의 역(각 6곳까지)을 보고
 * - 한 노선으로 가면 직통, 아니면 두 노선이 같이 서는 역에서 한 번 갈아타기까지
 * - 비교 값(분) = 걷기(80m/분) + 타는 거리 ÷ 속도(철도 600m/분, 버스 250m/분) + 정거장마다 0.5분 + 갈아타기 10분 + 버스 3분
 *   + 화면에 보이기 나쁜 노선 이름 조금: 직통 운전 노선("京浜電鉄 - 都営浅草線快特 - …") 3분, 색이 없는 노선(국토수치정보 "1号線(御堂筋線)") 3분
 * - 특급·신칸센·라이너(따로 요금이 드는 열차)와 배는 시내 구간에 쓰지 않는다
 * - 직선 600m 미만이면 걷는 구간이라 찾지 않는다
 * - 800m 안에 맞는 역이 없으면 1.5km 까지 넓혀 보되, 그 길이 다 걷는 것보다 빠를 때만
 *
 * 노선은 DB(transit_routes·transit_stops, transit 프로필로 넣음)에서 처음 쓸 때 한 번 읽어 메모리에 둔다. 표가 비어 있으면 늘 빈 결과.
 */
@Slf4j
@Component
public class TransitGuide {

    static final double WALK_RADIUS = 800;
    /** 800m 안에서 못 찾으면 한 번 더 넓혀 본다 (역에서 먼 전망대·공원). 그래도 다 걷는 것보다 빠를 때만 */
    static final double WIDE_WALK_RADIUS = 1500;
    static final int NEAR_LIMIT = 6;
    static final int NEAR_POOL = 60;
    static final int EXTRA_RAIL = 2;
    static final double MIN_LEG_METERS = 600;
    static final double WALK_PER_MIN = 80;
    static final double RAIL_PER_MIN = 600;
    static final double BUS_PER_MIN = 250;
    static final double PER_STOP = 0.5;
    static final double TRANSFER = 10;
    static final double BUS_PENALTY = 3;
    static final double THROUGH_PENALTY = 3;
    static final double NO_COLOUR_PENALTY = 3;
    private static final Pattern THROUGH_SERVICE = Pattern.compile(" - |直通|アクセス|エアポート");
    // 특급·신칸센 (열차 이름만 있는 노선 포함: のぞみ·ソニック 등)
    private static final Pattern PAID_EXPRESS = Pattern.compile("特急|新幹線|ライナー|ロマンスカー|はるか|ラピート|スカイライナー|成田エクスプレス"
            + "|Shinkansen|Limited Express|^(のぞみ|ひかり|こだま|みずほ|さくら|つばめ|はやぶさ|やまびこ|なすの|とき|たにがわ|かがやき|はくたか"
            + "|あさま|つるぎ|ソニック|にちりん|ゆふ|ゆふいんの森|かもめ|みどり|ハウステンボス|きらめき|かいおう|サンダーバード|しらさぎ|ひだ|しなの"
            + "|くろしお|こうのとり|きのさき|はまかぜ|スーパーはくと|あずさ|かいじ|ひたち|ときわ|踊り子|サフィール踊り子|スペーシア|けごん|きぬ"
            + "|ふじかわ|富士回遊|カムイ|すずらん|北斗|ライラック|おおぞら|とかち)");

    private final JdbcTemplate jdbc;
    private volatile TransitGraph graph;

    /** 생성자가 둘이라 Spring 이 쓸 쪽을 표시한다 (10-11 기동 사고와 같은 이유) */
    @Autowired
    public TransitGuide(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 테스트용: 그래프를 직접 준다 */
    TransitGuide(TransitGraph graph) {
        this.jdbc = null;
        this.graph = graph;
    }

    /** 앞 장소 → 다음 장소의 대강의 경로. 가까운 역이 없거나 노선으로 이어지지 않으면 empty */
    public Optional<TransitPlan> plan(double fromLat, double fromLng, double toLat, double toLng) {
        if (GeoUtils.distanceMeters(fromLat, fromLng, toLat, toLng) < MIN_LEG_METERS) return Optional.empty();
        TransitGraph g = graph();
        if (g.stationCount() == 0) return Optional.empty();
        Optional<Candidate> near = search(g, fromLat, fromLng, toLat, toLng, WALK_RADIUS);
        if (near.isPresent()) return near.map(c -> c.toPlan(g));
        double walkAll = GeoUtils.distanceMeters(fromLat, fromLng, toLat, toLng) / WALK_PER_MIN;
        return search(g, fromLat, fromLng, toLat, toLng, WIDE_WALK_RADIUS)
                .filter(c -> c.cost < walkAll)
                .map(c -> c.toPlan(g));
    }

    private Optional<Candidate> search(TransitGraph g, double fromLat, double fromLng, double toLat, double toLng, double radius) {
        List<int[]> starts = usableNear(g, fromLat, fromLng, radius);
        List<int[]> ends = usableNear(g, toLat, toLng, radius);
        if (starts.isEmpty() || ends.isEmpty()) return Optional.empty();

        // 내릴 역 후보를 노선별로: 노선 번호 → [위치, 역, 걷기 거리]
        Map<Integer, List<int[]>> endsByLine = new HashMap<>();
        for (int[] e : ends) {
            for (TransitGraph.Stop s : g.stopsAt(e[0])) {
                if (usable(g.line(s.line()))) endsByLine.computeIfAbsent(s.line(), k -> new ArrayList<>()).add(new int[]{s.position(), e[0], e[1]});
            }
        }

        Candidate best = null;
        for (int[] st : starts) {
            for (TransitGraph.Stop a : g.stopsAt(st[0])) {
                TransitGraph.Line lineA = g.line(a.line());
                if (!usable(lineA)) continue;
                // 직통
                for (int[] e : endsByLine.getOrDefault(a.line(), List.of())) {
                    if (e[0] == a.position()) continue;
                    double cost = walk(st[1]) + walk(e[2]) + ride(g, lineA, a.position(), e[0]);
                    if (best == null || cost < best.cost) {
                        best = new Candidate(cost, st, e, a, null, null);
                    }
                }
                // 한 번 갈아타기: 노선 A 의 역마다 그 역에 서는 다른 노선 B 가 내릴 역에 가는지
                int[] seq = lineA.stations();
                for (int pos = 0; pos < seq.length; pos++) {
                    if (pos == a.position()) continue;
                    double rideA = ride(g, lineA, a.position(), pos);
                    double base = walk(st[1]) + rideA + TRANSFER;
                    if (best != null && base >= best.cost) continue;
                    for (TransitGraph.Stop b : g.stopsAt(seq[pos])) {
                        if (b.line() == a.line()) continue;
                        TransitGraph.Line lineB = g.line(b.line());
                        for (int[] e : endsByLine.getOrDefault(b.line(), List.of())) {
                            if (e[0] == b.position()) continue;
                            double cost = base + walk(e[2]) + ride(g, lineB, b.position(), e[0]);
                            if (best == null || cost < best.cost) {
                                best = new Candidate(cost, st, e, a, new TransitGraph.Stop(a.line(), pos), new TransitGraph.Stop(b.line(), b.position()));
                            }
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** 비교 값 계산 중의 후보. transferA = 노선 A 에서 내리는 위치, transferB = 노선 B 에서 타는 위치 (직통이면 null) */
    private record Candidate(double cost, int[] start, int[] end, TransitGraph.Stop board,
                             TransitGraph.Stop transferA, TransitGraph.Stop transferB) {

        TransitPlan toPlan(TransitGraph g) {
            TransitGraph.Station from = g.station(start[0]);
            TransitGraph.Station to = g.station(end[1]);
            List<TransitPlan.Segment> segments = new ArrayList<>();
            if (transferA == null) {
                segments.add(segment(g, board.line(), board.position(), end[0]));
            } else {
                segments.add(segment(g, board.line(), board.position(), transferA.position()));
                segments.add(segment(g, transferB.line(), transferB.position(), end[0]));
            }
            return new TransitPlan(from.name(), from.nameKo(), start[1], to.name(), to.nameKo(), end[2], List.copyOf(segments));
        }

        private static TransitPlan.Segment segment(TransitGraph g, int lineIndex, int fromPos, int toPos) {
            TransitGraph.Line line = g.line(lineIndex);
            TransitGraph.Station a = g.station(line.stations()[fromPos]);
            TransitGraph.Station b = g.station(line.stations()[toPos]);
            return new TransitPlan.Segment(line.name(), line.mode(), line.ref(), line.colour(), line.operator(),
                    a.name(), a.nameKo(), b.name(), b.nameKo(), Math.abs(toPos - fromPos));
        }
    }

    /** 걸어갈 수 있는 역 중 쓸 수 있는 노선이 서는 곳 (가까운 순 NEAR_LIMIT 곳). 신칸센 승강장처럼 따로 묶인 역은 건너뛴다 */
    private static List<int[]> usableNear(TransitGraph g, double lat, double lng, double radius) {
        List<int[]> result = new ArrayList<>();
        int rail = 0;
        for (int[] n : g.near(lat, lng, radius, NEAR_POOL)) {
            boolean usable = g.stopsAt(n[0]).stream().anyMatch(s -> usable(g.line(s.line())));
            if (!usable) continue;
            boolean hasRail = g.stopsAt(n[0]).stream()
                    .anyMatch(s -> usable(g.line(s.line())) && !"bus".equals(g.line(s.line()).mode()));
            // 가까운 역 6곳 + 버스 정류장에 밀린 철도역 2곳까지 (교토 시내처럼 정류장이 촘촘한 곳)
            if (result.size() < NEAR_LIMIT) {
                result.add(n);
                if (hasRail) rail++;
            } else if (hasRail && rail < EXTRA_RAIL) {
                result.add(n);
                rail++;
            }
            if (result.size() >= NEAR_LIMIT && rail >= EXTRA_RAIL) break;
        }
        return result;
    }

    private static double walk(int meters) {
        return meters / WALK_PER_MIN;
    }

    private static double ride(TransitGraph g, TransitGraph.Line line, int from, int to) {
        boolean bus = "bus".equals(line.mode());
        return g.rideMeters(line, from, to) / (bus ? BUS_PER_MIN : RAIL_PER_MIN)
                + Math.abs(to - from) * PER_STOP + (bus ? BUS_PENALTY : 0)
                + (THROUGH_SERVICE.matcher(line.name()).find() ? THROUGH_PENALTY : 0)
                + (line.colour() == null || line.colour().isBlank() ? NO_COLOUR_PENALTY : 0);
    }

    /** 시내 구간에 쓸 노선인지 (따로 요금이 드는 특급·신칸센은 뺀다) */
    static boolean usable(TransitGraph.Line line) {
        return line.name() != null && !PAID_EXPRESS.matcher(line.name()).find();
    }

    /** 처음 쓸 때 DB에서 한 번 읽는다 */
    TransitGraph graph() {
        TransitGraph g = graph;
        if (g != null) return g;
        synchronized (this) {
            if (graph == null) graph = load();
            return graph;
        }
    }

    private TransitGraph load() {
        try {
            List<TransitGraph.StopRow> stops = jdbc.query(
                    "SELECT id, name, name_en, name_ko, lat, lng FROM public.transit_stops ORDER BY id",
                    (rs, n) -> new TransitGraph.StopRow(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getDouble(5), rs.getDouble(6)));
            List<TransitGraph.RouteRow> routes = jdbc.query(
                    "SELECT id, mode, ref, line, operator, colour, stop_ids FROM public.transit_routes ORDER BY id",
                    (rs, n) -> new TransitGraph.RouteRow(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), Arrays.asList(rs.getString(7).split(","))));
            TransitGraph g = new TransitGraph(stops, routes);
            log.info("[Transit] 노선 {} · 역 {} 읽음", g.lineCount(), g.stationCount());
            return g;
        } catch (RuntimeException e) {
            log.warn("[Transit] 노선을 읽지 못해 경로 안내 없이 진행: {}", e.getClass().getSimpleName());
            return TransitGraph.EMPTY;
        }
    }
}
