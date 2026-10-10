package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelLeg;
import com.example.japantravelplanner.itinerary.travel.TravelMatrix;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 하루 일정에 시각을 붙인다.
 *
 * 순서: 숙소 → 오전 관광지 → 점심 → 오후 관광지 → 저녁 → 야경 장소
 * 시각: 이동 시간 + 10분 여유 → (식사는 시간대까지 대기) → 체류 시간
 *
 * 시간이 안 맞으면 고친다.
 *  - 점심이 13:30을 넘기면: 오전 관광지 중 점수 낮은 곳을 오후로 옮김 (오후에서 넘치면 아래 규칙으로 빠짐)
 *  - 저녁이 20:00을 넘기거나 하루가 끝나는 시각을 넘기면: 점수 낮은 관광지부터 제외
 *    (점심이 있으면 오후 → 오전 순서, 없으면 전체에서 점수 낮은 순). 제외해도 나아지지 않으면 야경 장소를 제외
 *
 * 뺀 관광지 중 지금 일정에 혼자 다시 들어갈 수 있는 곳은 되돌려 넣는다 (점수 높은 순)
 *  - 10-10 여행 8 v10 마지막 날(09:30~12:00): 박물관 2곳(115분)을 빼다가 40분짜리 平将門塚까지 빠져 하루가 비었다
 * 고친 뒤에도 저녁 전에 1시간 이상 비면(식사 시간대 대기) 예비 관광지를 끼워 넣는다
 *
 * 이동 시간 표는 하루에 한 번만 만든다 (Google 실시간 도보 조회가 켜져 있어도 호출은 한 번)
 * 하루 도보 거리 제한("하루 2km 이내 도보")이 있으면 탈것 표를 하나 더 만들어, 걸은 거리가 제한을 넘는 구간부터 탈것으로 바꾼다
 * 단, 끼워 넣어서 이동이 15분 넘게 늘어나는 예비는 쓰지 않는다 (멀리 돌아가는 일정 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayScheduler {

    /** 이동마다 더하는 여유 시간 (길 찾기·신호·역 안에서 걷기) */
    static final int MOVE_BUFFER_MINUTES = 10;

    /** 저녁 전에 이만큼 이상 기다리면 예비 관광지로 채운다 */
    static final int FILL_IDLE_MINUTES = 60;

    /**
     * 예비 관광지를 끼워 넣어서 늘어나는 이동 시간의 상한(분). 이보다 많이 돌아가야 하면 그 예비는 쓰지 않는다.
     * (점심 뒤에 롯폰기 쪽으로 되돌아가는 なだれ坂가 들어가서 하루 이동이 18분 늘어난 적이 있다)
     */
    static final int MAX_FILL_DETOUR_MINUTES = 15;

    private static final int MAX_REPAIRS = 40;

    private final TravelTimeService travelTimeService;

    public ScheduledDay schedule(DayRoute route, DayMeals meals, DayWindow window,
                                 TravelPoint hotel, TravelOptions options, double stayMultiplier) {
        DayZone day = route.day();
        List<String> warnings = new ArrayList<>();
        List<DroppedStop> dropped = new ArrayList<>();

        if (window.isEmpty()) {
            warnings.add("도착·출발 시간 때문에 이 날은 일정을 넣을 수 없어요.");
            return new ScheduledDay(day.dayNumber(), day.date(), day.zone(), List.of(), dropped, warnings);
        }

        // ① 항목과 좌표 모으기 (좌표 번호 0 = 숙소)
        List<TravelPoint> points = new ArrayList<>();
        points.add(hotel);
        Plan plan = new Plan();
        for (ScoredAttraction a : route.morning()) {
            plan.morning.add(attractionItem(a, points, stayMultiplier));
        }
        for (ScoredAttraction a : route.afternoon()) {
            plan.afternoon.add(attractionItem(a, points, stayMultiplier));
        }
        plan.lunch = mealItem(StopType.LUNCH, meals.lunch(), points, stayMultiplier, warnings);
        plan.dinner = mealItem(StopType.DINNER, meals.dinner(), points, stayMultiplier, warnings);

        if (route.night() != null) {
            if (plan.dinner != null) {
                plan.night = nightItem(route.night(), points, stayMultiplier);
            } else {
                dropped.add(new DroppedStop(route.night().attraction().getName(), DropReason.NIGHT_WITHOUT_DINNER));
            }
        }

        List<Item> spares = new ArrayList<>();
        for (ScoredAttraction a : route.spares()) {
            spares.add(attractionItem(a, points, stayMultiplier));
        }

        Context ctx = context(points, options, toMinutes(window.start()), toMinutes(window.end()));

        // ② 시간이 안 맞으면 고치기
        List<Item> droppedItems = new ArrayList<>();
        for (int i = 0; i < MAX_REPAIRS; i++) {
            Sim sim = simulate(plan, ctx);

            if (sim.lunchLate && !plan.morning.isEmpty()) {
                plan = moveLowestMorningToAfternoon(plan, ctx);
                continue;
            }
            if (sim.dinnerLate || sim.endExceeded) {
                Plan next = dropOne(plan, sim, ctx, dropped, droppedItems);
                if (next != null) {
                    plan = next;
                    continue;
                }
            }
            break;   // 문제가 없거나, 더 고칠 수 없음
        }

        // ②-1 뺀 곳 중 혼자 다시 들어가는 곳은 되돌리기
        plan = restoreDropped(plan, droppedItems, ctx, dropped);

        // ③ 저녁 전에 시간이 많이 비면 예비 관광지로 채우기 (시간이 모자라 뺀 날은 건너뜀)
        if (dropped.stream().noneMatch(d -> d.reason() == DropReason.TIME_SHORTAGE)) {
            plan = fillIdleTime(plan, spares, ctx);
        }

        // ④ 결과
        Sim finalSim = simulate(plan, ctx);
        if (finalSim.lunchLate) warnings.add("점심 시간이 13:30을 넘겼어요.");
        if (finalSim.dinnerLate) warnings.add("저녁 시간이 20:00을 넘겼어요.");
        if (finalSim.endExceeded) warnings.add("일정이 " + window.end() + "을 넘겨 끝나요.");

        List<ScheduledStop> stops = finalSim.timed.stream()
                .map(t -> new ScheduledStop(t.item.type, t.item.attraction, t.item.meal,
                        toTime(t.arrival), toTime(t.departure), t.item.stay, t.waited, t.leg))
                .toList();

        log.info("[Schedule] DAY{} {} / 빠진 곳 {} / {}", day.dayNumber(), day.zone().getLabel(),
                dropped.stream().map(DroppedStop::name).toList(),
                stops.stream().map(s -> s.arrival() + " " + s.name()).toList());
        return new ScheduledDay(day.dayNumber(), day.date(), day.zone(), stops, List.copyOf(dropped), List.copyOf(warnings));
    }

    // ── 순서는 그대로 두고 시각만 다시 계산 (식당 교체용) ──

    /** 다시 계산할 항목. point는 이동 시간 계산에 쓸 좌표, stayMinutes는 이미 정해진 체류 시간 */
    public record RetimeItem(StopType type, TravelPoint point, int stayMinutes) {
    }

    /** 다시 계산된 시각 (입력 항목과 같은 순서) */
    public record RetimedStop(LocalTime arrival, LocalTime departure, int waitMinutes, TravelLeg travelIn) {
    }

    /** @param warnings 시간이 안 맞는 경우의 안내 문장 (관광지를 빼거나 옮기지는 않는다) */
    public record Retimed(List<RetimedStop> stops, List<String> warnings) {
    }

    /**
     * 정해진 순서 그대로 시각만 다시 계산한다. 식당을 바꿨을 때처럼 장소 하나만 달라진 일정에 쓴다.
     * 처음 만들 때와 같은 규칙(이동 + 10분 여유, 식사 시간대 대기)을 쓰지만, 시간이 모자라도 관광지를 빼지 않고
     * 안내 문장으로만 알린다. 사용자가 고른 일정을 서버가 마음대로 줄이면 안 되기 때문이다.
     */
    public Retimed retime(List<RetimeItem> items, LocalTime windowStart, LocalTime windowEnd,
                          TravelPoint hotel, TravelOptions options) {
        List<TravelPoint> points = new ArrayList<>();
        points.add(hotel);
        List<Item> sequence = new ArrayList<>();
        for (RetimeItem item : items) {
            int index = points.size();
            points.add(item.point());
            sequence.add(new Item(item.type(), null, null, index, item.stayMinutes(), 0));
        }

        Sim sim = simulateSequence(sequence, context(points, options, toMinutes(windowStart), toMinutes(windowEnd)));

        List<String> warnings = new ArrayList<>();
        if (sim.lunchLate) warnings.add("점심 시간이 13:30을 넘겼어요.");
        if (sim.dinnerLate) warnings.add("저녁 시간이 20:00을 넘겼어요.");
        if (sim.endExceeded) warnings.add("일정이 " + windowEnd + "을 넘겨 끝나요.");

        List<RetimedStop> stops = sim.timed.stream()
                .map(t -> new RetimedStop(toTime(t.arrival), toTime(t.departure), t.waited, t.leg))
                .toList();
        return new Retimed(stops, List.copyOf(warnings));
    }

    // ── 시간 계산 ──

    /**
     * 이동 시간 표. 하루 도보 제한이 있으면 걷는 기준 0분으로 만든 탈것 표도 같이 둔다
     * (탈것 표도 아주 가까운 곳은 걷는 쪽이 빨라서 걷기로 남는다)
     */
    private Context context(List<TravelPoint> points, TravelOptions options, int start, int end) {
        TravelMatrix matrix = travelTimeService.buildMatrix(points, options.mode(), options.walkThresholdMinutes());
        if (options.dailyWalkMeters() == null) {
            return new Context(matrix, null, Integer.MAX_VALUE, start, end);
        }
        TravelMatrix rides = travelTimeService.buildMatrix(points, options.mode(), 0);
        return new Context(matrix, rides, options.dailyWalkMeters(), start, end);
    }

    /** 계획대로 하루를 보냈을 때의 시각표와 문제점 */
    static Sim simulate(Plan plan, Context ctx) {
        return simulateSequence(plan.sequence(), ctx);
    }

    /** 정해진 순서 그대로 하루를 보냈을 때의 시각표와 문제점 */
    static Sim simulateSequence(List<Item> sequence, Context ctx) {
        List<Timed> timed = new ArrayList<>();
        boolean lunchLate = false;
        boolean dinnerLate = false;
        int now = ctx.start;
        int prev = 0;
        int walked = 0;

        for (Item item : sequence) {
            TravelLeg leg = ctx.matrix.get(prev, item.pointIndex);
            // 하루 도보 제한: 이 구간까지 걸으면 넘는 경우 탈것으로
            if (leg.mode() == LegMode.WALK && ctx.rides != null && walked + leg.distanceMeters() > ctx.walkBudget) {
                leg = ctx.rides.get(prev, item.pointIndex);
            }
            if (leg.mode() == LegMode.WALK) walked += leg.distanceMeters();
            int arrive = now + leg.minutes() + MOVE_BUFFER_MINUTES;
            int begin = arrive;

            if (item.type == StopType.LUNCH) {
                begin = Math.max(arrive, DayWindow.LUNCH_EARLIEST);
                if (begin > DayWindow.LUNCH_LATEST) lunchLate = true;
            } else if (item.type == StopType.DINNER) {
                begin = Math.max(arrive, DayWindow.DINNER_EARLIEST);
                if (begin > DayWindow.DINNER_LATEST) dinnerLate = true;
            }

            int departure = begin + item.stay;
            timed.add(new Timed(item, begin, begin - arrive, departure, leg));
            now = departure;
            prev = item.pointIndex;
        }
        return new Sim(timed, lunchLate, dinnerLate, now > ctx.end, now);
    }

    // ── 고치기 ──

    /** 오전 관광지 중 점수가 가장 낮은 곳을 오후로 옮긴다. 오후에서 하루가 가장 일찍 끝나는 자리에 넣음 */
    private Plan moveLowestMorningToAfternoon(Plan plan, Context ctx) {
        Item lowest = ascendingByScore(plan.morning).get(0);
        Plan base = plan.without(lowest);

        Plan best = null;
        int bestEnd = Integer.MAX_VALUE;
        for (int position = 0; position <= base.afternoon.size(); position++) {
            Plan trial = base.withAfternoonInserted(lowest, position);
            int end = simulate(trial, ctx).end;
            if (end < bestEnd) {
                bestEnd = end;
                best = trial;
            }
        }
        log.debug("[Schedule] 점심이 늦어 오전 관광지를 오후로 이동: {}", lowest.attraction.attraction().getName());
        return best;
    }

    /**
     * 점수 낮은 관광지부터 하나씩 빼보고, 문제가 줄어드는 첫 번째를 고른다.
     * 어떤 관광지를 빼도 나아지지 않으면(식사 시간 대기 때문 등) 야경 장소를 뺀다.
     * 더 뺄 것이 없으면 null
     */
    private Plan dropOne(Plan plan, Sim current, Context ctx, List<DroppedStop> dropped, List<Item> droppedItems) {
        List<Item> candidates = new ArrayList<>();
        if (plan.lunch != null) {
            candidates.addAll(ascendingByScore(plan.afternoon));   // 점심 뒤(오후)부터
            candidates.addAll(ascendingByScore(plan.morning));
        } else {
            List<Item> all = new ArrayList<>(plan.morning);
            all.addAll(plan.afternoon);
            candidates.addAll(ascendingByScore(all));
        }
        if (plan.night != null) candidates.add(plan.night);

        for (Item candidate : candidates) {
            Plan trial = plan.without(candidate);
            if (isBetter(simulate(trial, ctx), current)) {
                dropped.add(new DroppedStop(candidate.attraction.attraction().getName(), DropReason.TIME_SHORTAGE));
                if (candidate != plan.night) droppedItems.add(candidate);
                return trial;
            }
        }
        return null;
    }

    /**
     * 시간이 모자라 뺀 관광지를 점수 높은 순으로 하나씩 다시 넣어본다. 문제(저녁 지연·하루 초과·점심 지연)가 생기지 않는
     * 자리가 있으면 하루가 가장 일찍 끝나는 자리에 넣고 '빠진 곳'에서 지운다. 야경 장소는 되돌리지 않는다.
     * 긴 곳부터 빼다 보면 짧은 곳까지 빠져 하루가 비는 경우를 막는다.
     */
    private Plan restoreDropped(Plan plan, List<Item> droppedItems, Context ctx, List<DroppedStop> dropped) {
        List<Item> remaining = new ArrayList<>(droppedItems);
        remaining.sort(Comparator.comparingDouble((Item item) -> item.score).reversed());
        boolean restored = true;
        while (restored) {
            restored = false;
            for (Item item : List.copyOf(remaining)) {
                Plan best = null;
                int bestEnd = Integer.MAX_VALUE;
                for (Plan trial : insertions(plan, item)) {
                    Sim sim = simulate(trial, ctx);
                    if (sim.problems() == 0 && !sim.lunchLate && sim.end < bestEnd) {
                        bestEnd = sim.end;
                        best = trial;
                    }
                }
                if (best != null) {
                    plan = best;
                    remaining.remove(item);
                    String name = item.attraction.attraction().getName();
                    dropped.removeIf(d -> d.name().equals(name) && d.reason() == DropReason.TIME_SHORTAGE);
                    log.debug("[Schedule] 뺀 관광지를 다시 넣음: {}", name);
                    restored = true;
                }
            }
        }
        return plan;
    }

    /** 관광지 하나를 넣을 수 있는 모든 자리 (점심이 있으면 점심 앞·뒤 모두) */
    private static List<Plan> insertions(Plan plan, Item item) {
        List<Plan> result = new ArrayList<>();
        for (int position = 0; position <= plan.morning.size(); position++) {
            Plan p = plan.copy();
            p.morning.add(position, item);
            result.add(p);
        }
        if (plan.lunch != null) {
            for (int position = 0; position <= plan.afternoon.size(); position++) {
                result.add(plan.withAfternoonInserted(item, position));
            }
        }
        return result;
    }

    /**
     * 저녁 식사 대기가 길면 예비 관광지(점수 높은 순)를 오후에 끼워 넣는다.
     * 문제가 생기지 않고 대기가 줄어드는 자리 중 이동이 가장 짧은 곳에 넣는다. 넣어도 안 되는 예비는 건너뜀
     */
    private Plan fillIdleTime(Plan plan, List<Item> spares, Context ctx) {
        for (Item spare : spares) {
            Sim current = simulate(plan, ctx);
            if (current.dinnerWait() < FILL_IDLE_MINUTES) break;

            Plan best = null;
            int bestTravel = Integer.MAX_VALUE;
            for (int position = 0; position <= plan.afternoon.size(); position++) {
                Plan trial = plan.withAfternoonInserted(spare, position);
                Sim sim = simulate(trial, ctx);
                boolean ok = sim.problems() == 0 && !sim.lunchLate && sim.dinnerWait() < current.dinnerWait()
                        && sim.travelMinutes() - current.travelMinutes() <= MAX_FILL_DETOUR_MINUTES;
                if (ok && sim.travelMinutes() < bestTravel) {
                    bestTravel = sim.travelMinutes();
                    best = trial;
                }
            }
            if (best != null) {
                log.debug("[Schedule] 저녁 전 대기가 길어 예비 관광지 추가: {}", spare.attraction.attraction().getName());
                plan = best;
            }
        }
        return plan;
    }

    private static boolean isBetter(Sim candidate, Sim current) {
        int a = candidate.problems();
        int b = current.problems();
        return a < b || (a == b && candidate.end < current.end);
    }

    private static List<Item> ascendingByScore(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingDouble(item -> item.score));
        return sorted;
    }

    // ── 항목 만들기 ──

    private static Item attractionItem(ScoredAttraction a, List<TravelPoint> points, double multiplier) {
        int index = points.size();
        points.add(new TravelPoint(a.attraction().getName(), a.lat(), a.lng()));
        int stay = StayTimes.scaled(StayTimes.baseOf(a.attraction().getTypes()), multiplier);
        return new Item(StopType.ATTRACTION, a, null, index, stay, a.score());
    }

    /** 저녁 뒤 야경 장소 (체류 시간이 낮과 다르다: StayTimes.nightBaseOf) */
    private static Item nightItem(ScoredAttraction a, List<TravelPoint> points, double multiplier) {
        int index = points.size();
        points.add(new TravelPoint(a.attraction().getName(), a.lat(), a.lng()));
        int stay = StayTimes.scaled(StayTimes.nightBaseOf(a.attraction().getTypes()), multiplier);
        return new Item(StopType.ATTRACTION, a, null, index, stay, a.score());
    }

    /** 끼니가 없거나(시간대 없음) 식당 후보가 없으면 null */
    private static Item mealItem(StopType type, PlannedMeal planned, List<TravelPoint> points,
                                 double multiplier, List<String> warnings) {
        if (planned == null) return null;
        String label = type == StopType.LUNCH ? "점심" : "저녁";
        if (!planned.found()) {
            warnings.add(label + " 식당 후보를 찾지 못했어요. 조건을 조금 넓혀서 다시 찾아볼 수 있어요.");
            return null;
        }
        Restaurant r = planned.chosen().restaurant();
        int index = points.size();
        points.add(new TravelPoint(r.getName(), r.getLocation().getY(), r.getLocation().getX()));
        int stay = StayTimes.scaled(type == StopType.LUNCH ? StayTimes.LUNCH : StayTimes.DINNER, multiplier);
        return new Item(type, null, planned, index, stay, 0);
    }

    // ── 시각 변환 ──

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime toTime(int minutes) {
        int clamped = Math.min(Math.max(minutes, 0), 23 * 60 + 59);
        return LocalTime.of(clamped / 60, clamped % 60);
    }

    // ── 내부 자료 ──

    /**
     * @param rides      걷는 기준 0분으로 만든 탈것 표 (하루 도보 제한이 없으면 null)
     * @param walkBudget 하루에 걸을 최대 거리(미터)
     */
    private record Context(TravelMatrix matrix, TravelMatrix rides, int walkBudget, int start, int end) {
    }

    /** 일정 항목 하나. pointIndex는 이동 시간 표의 번호 */
    private static final class Item {
        final StopType type;
        final ScoredAttraction attraction;
        final PlannedMeal meal;
        final int pointIndex;
        final int stay;
        final double score;

        Item(StopType type, ScoredAttraction attraction, PlannedMeal meal, int pointIndex, int stay, double score) {
            this.type = type;
            this.attraction = attraction;
            this.meal = meal;
            this.pointIndex = pointIndex;
            this.stay = stay;
            this.score = score;
        }
    }

    /** 하루 계획. 바꿀 때는 복사본을 만들어서 비교해보고 고른다 */
    private static final class Plan {
        List<Item> morning = new ArrayList<>();
        List<Item> afternoon = new ArrayList<>();
        Item lunch;
        Item dinner;
        Item night;

        List<Item> sequence() {
            List<Item> all = new ArrayList<>(morning);
            if (lunch != null) all.add(lunch);
            all.addAll(afternoon);
            if (dinner != null) all.add(dinner);
            if (night != null) all.add(night);
            return all;
        }

        Plan copy() {
            Plan p = new Plan();
            p.morning = new ArrayList<>(morning);
            p.afternoon = new ArrayList<>(afternoon);
            p.lunch = lunch;
            p.dinner = dinner;
            p.night = night;
            return p;
        }

        Plan without(Item item) {
            Plan p = copy();
            p.morning.remove(item);
            p.afternoon.remove(item);
            if (p.night == item) p.night = null;
            return p;
        }

        Plan withAfternoonInserted(Item item, int position) {
            Plan p = copy();
            p.afternoon.add(position, item);
            return p;
        }
    }

    private record Timed(Item item, int arrival, int waited, int departure, TravelLeg leg) {
    }

    private record Sim(List<Timed> timed, boolean lunchLate, boolean dinnerLate, boolean endExceeded, int end) {

        /** 관광지를 빼서 고칠 수 있는 문제의 수 (점심 지연은 옮겨서 고치므로 제외) */
        int problems() {
            return (dinnerLate ? 1 : 0) + (endExceeded ? 1 : 0);
        }

        /** 저녁 시간대를 기다린 시간 (저녁이 없으면 0) */
        int dinnerWait() {
            return timed.stream().filter(t -> t.item.type == StopType.DINNER).mapToInt(Timed::waited).sum();
        }

        /** 하루 이동 시간 합 (여유 10분 제외) */
        int travelMinutes() {
            return timed.stream().mapToInt(t -> t.leg.minutes()).sum();
        }
    }
}