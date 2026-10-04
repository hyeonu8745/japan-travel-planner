package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.itinerary.travel.TravelMatrix;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.trip.entity.TravelPace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 하루의 관광지 방문 순서를 정한다 (TSP).
 *
 * 1. 야경 관심사면 후보 중 전망대 한 곳을 저녁 후 장소로 뺀다 (낮 관광지 수는 그대로)
 * 2. 남은 후보 상위 perDay곳을 숙소에서 출발하는 모든 순서(최대 5! = 120가지)로 비교해
 *    이동 시간 합이 가장 짧은 순서를 고른다. 끝은 야경 장소가 있으면 그곳, 없으면 숙소로 돌아오는 것까지 계산
 * 3. 앞에서 오전 개수만큼 자르면 오전/오후
 *
 * 관광지가 많아야 5곳이라 모든 경우를 비교해도 충분히 빠르고, 항상 최적이 나온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteOptimizer {

    static final int MAX_STOPS = 7;               // 7! = 5,040. 이 이상은 받지 않음
    static final String NIGHT_TYPE = "observation_deck";

    private final TravelTimeService travelTimeService;

    public DayRoute optimize(DayZone day, TravelPoint hotel, TravelPace pace,
                             TravelOptions options, boolean nightView) {
        List<ScoredAttraction> pool = new ArrayList<>(day.candidates());
        if (pool.isEmpty()) {
            log.warn("[Route] DAY{} {} 관광지 후보 없음", day.dayNumber(), day.zone().getLabel());
            return DayRoute.empty(day);
        }

        // ① 야경 장소 (점수 가장 높은 전망대)
        ScoredAttraction night = nightView ? pickNight(pool) : null;
        if (night != null) pool.remove(night);

        // ② 낮 관광지와 예비 후보
        int perDay = Math.min(pace.attractionsPerDay(), pool.size());
        List<ScoredAttraction> stops = pool.subList(0, perDay);
        List<ScoredAttraction> spares = List.copyOf(pool.subList(perDay, pool.size()));

        // 이동 시간 표: 0 = 숙소, 1..n = 낮 관광지, n+1 = 야경 장소(있으면)
        List<TravelPoint> points = new ArrayList<>();
        points.add(hotel);
        stops.forEach(s -> points.add(toPoint(s)));
        if (night != null) points.add(toPoint(night));
        TravelMatrix matrix = travelTimeService.buildMatrix(points, options.mode(), options.walkThresholdMinutes());

        int end = night != null ? stops.size() + 1 : 0;
        int[] order = bestOrder(matrix, stops.size(), end);

        // ③ 순서대로 정리하고 오전/오후로 자르기
        List<ScoredAttraction> ordered = new ArrayList<>();
        List<TravelLeg> legs = new ArrayList<>();
        int prev = 0;
        for (int idx : order) {
            ordered.add(stops.get(idx - 1));
            legs.add(matrix.get(prev, idx));
            prev = idx;
        }
        TravelLeg endLeg = prev == end ? null : matrix.get(prev, end);
        int total = pathMinutes(matrix, order, end);

        int morningCount = Math.min(pace.getMorningCount(), ordered.size());
        DayRoute route = new DayRoute(day,
                List.copyOf(ordered.subList(0, morningCount)),
                List.copyOf(ordered.subList(morningCount, ordered.size())),
                night, spares, List.copyOf(legs), endLeg, total);

        log.info("[Route] DAY{} {} 오전 {} / 오후 {} / 야경 {} / 이동 {}분",
                day.dayNumber(), day.zone().getLabel(),
                names(route.morning()), names(route.afternoon()),
                night == null ? "-" : night.attraction().getName(), total);
        return route;
    }

    /**
     * 0번(출발)에서 시작해 1..stopCount번을 모두 한 번씩 들르고 end번에서 끝나는 가장 짧은 순서.
     * end가 0이면 출발지로 돌아오는 것까지 계산한다.
     * 이동 시간이 같으면 먼저 찾은 순서(점수 높은 관광지가 앞)를 유지한다.
     *
     * @return 방문할 번호(1..stopCount)의 순서
     */
    static int[] bestOrder(TravelMatrix matrix, int stopCount, int end) {
        if (stopCount > MAX_STOPS) {
            throw new IllegalArgumentException("관광지가 너무 많습니다: " + stopCount);
        }
        if (stopCount == 0) return new int[0];

        Search search = new Search(matrix, stopCount, end);
        search.permute(0, 0, 0);
        return search.best;
    }

    static int pathMinutes(TravelMatrix matrix, int[] order, int end) {
        int total = 0;
        int prev = 0;
        for (int idx : order) {
            total += matrix.minutes(prev, idx);
            prev = idx;
        }
        return total + matrix.minutes(prev, end);
    }

    /** 모든 순서를 깊이 우선으로 만들며, 이미 최선보다 길어진 순서는 더 보지 않는다 */
    private static final class Search {
        private final TravelMatrix matrix;
        private final int stopCount;
        private final int end;
        private final int[] current;
        private final boolean[] used;
        private int[] best;
        private int bestCost = Integer.MAX_VALUE;

        Search(TravelMatrix matrix, int stopCount, int end) {
            this.matrix = matrix;
            this.stopCount = stopCount;
            this.end = end;
            this.current = new int[stopCount];
            this.used = new boolean[stopCount + 1];
        }

        void permute(int depth, int prev, int cost) {
            if (cost >= bestCost) return;
            if (depth == stopCount) {
                int total = cost + matrix.minutes(prev, end);
                if (total < bestCost) {
                    bestCost = total;
                    best = current.clone();
                }
                return;
            }
            for (int next = 1; next <= stopCount; next++) {
                if (used[next]) continue;
                used[next] = true;
                current[depth] = next;
                permute(depth + 1, next, cost + matrix.minutes(prev, next));
                used[next] = false;
            }
        }
    }

    /** 후보는 점수순이라 처음 만나는 전망대가 점수 가장 높은 곳 */
    static ScoredAttraction pickNight(List<ScoredAttraction> candidates) {
        return candidates.stream()
                .filter(c -> hasType(c.attraction().getTypes(), NIGHT_TYPE))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasType(String typesCsv, String type) {
        if (typesCsv == null) return false;
        return Arrays.stream(typesCsv.split(",")).map(String::trim).anyMatch(type::equals);
    }

    private static TravelPoint toPoint(ScoredAttraction s) {
        return new TravelPoint(s.attraction().getName(), s.lat(), s.lng());
    }

    private static List<String> names(List<ScoredAttraction> list) {
        return list.stream().map(s -> s.attraction().getName()).toList();
    }
}