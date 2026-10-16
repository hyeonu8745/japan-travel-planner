package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.meal.FoodFilter;
import com.example.japantravelplanner.itinerary.meal.Meal;
import com.example.japantravelplanner.itinerary.meal.RestaurantCandidate;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimeItem;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.Retimed;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimedStop;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 오후 쉬는 시간 카페 넣기 (10-16). 하루 시각표가 다 정해진 뒤에 한 곳을 끼워 넣고 시각만 다시 계산한다.
 * - 점심과 저녁이 모두 있는 날만 (반나절 일정·도착 날 오후 늦게 시작하는 날은 넣지 않음)
 * - 점심 뒤 ~ 저녁 앞 사이에서 앞 장소를 떠나는 시각이 14:00~16:30인 자리, 15:00에 가장 가까운 곳
 * - 앞뒤 장소의 중간 근처 카페 (RestaurantFinder.findCafe), 여행 전체에서 같은 가게는 한 번, 끼니와 같은 음식 조건(피할 음식·알레르기)
 * - 넣고 다시 계산했을 때 새 시간 문제(저녁 지연·하루 초과)가 생기면 넣지 않는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CafePlanner {

    static final int ALTERNATIVES = 2;

    private final RestaurantFinder restaurantFinder;
    private final DayScheduler dayScheduler;
    private final CafeProperties props;

    public ScheduledDay addBreak(ScheduledDay day, DayWindow window, TravelPoint hotel, TravelOptions options,
                                 FoodFilter filter, Set<Long> usedRestaurantIds) {
        if (!props.isEnabled() || window.isEmpty()) return day;
        List<ScheduledStop> stops = day.stops();
        int lunch = indexOf(stops, StopType.LUNCH);
        int dinner = indexOf(stops, StopType.DINNER);
        if (lunch < 0 || dinner < 0 || dinner <= lunch) return day;

        int insertAt = insertPosition(stops, lunch, dinner);
        if (insertAt < 0) return day;

        TravelPoint before = pointOf(stops.get(insertAt - 1));
        TravelPoint after = pointOf(stops.get(insertAt));
        TravelPoint anchor = new TravelPoint("카페 기준(중간)", (before.lat() + after.lat()) / 2, (before.lng() + after.lng()) / 2);
        List<RestaurantCandidate> cafes = restaurantFinder.findCafe(anchor.lat(), anchor.lng(), filter, usedRestaurantIds,
                day.date(), props.getSearchRadii(), props.getMaxYen(), props.getDistancePenalty(), ALTERNATIVES + 1);
        if (cafes.isEmpty()) {
            log.info("[Cafe] DAY{} 근처 카페 없음", day.dayNumber());
            return day;
        }
        RestaurantCandidate chosen = cafes.get(0);
        TravelPoint cafePoint = new TravelPoint(chosen.restaurant().getName(),
                chosen.restaurant().getLocation().getY(), chosen.restaurant().getLocation().getX());

        List<RetimeItem> items = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            if (i == insertAt) items.add(new RetimeItem(StopType.CAFE, cafePoint, props.getStayMinutes()));
            ScheduledStop s = stops.get(i);
            items.add(new RetimeItem(s.type(), pointOf(s), s.stayMinutes()));
        }
        Retimed retimed = dayScheduler.retime(items, window.start(), window.end(), hotel, options);
        if (retimed.warnings().stream().anyMatch(w -> !day.warnings().contains(w))) {
            log.info("[Cafe] DAY{} 넣으면 시간이 모자라 넣지 않음: {}", day.dayNumber(), retimed.warnings());
            return day;
        }

        PlannedMeal cafeMeal = new PlannedMeal(Meal.CAFE, anchor, chosen, List.copyOf(cafes.subList(1, cafes.size())), false);
        List<ScheduledStop> result = new ArrayList<>();
        int r = 0;
        for (int i = 0; i <= stops.size(); i++) {
            if (i == insertAt) {
                RetimedStop t = retimed.stops().get(r++);
                result.add(new ScheduledStop(StopType.CAFE, null, cafeMeal, t.arrival(), t.departure(),
                        props.getStayMinutes(), t.waitMinutes(), t.travelIn()));
            }
            if (i == stops.size()) break;
            ScheduledStop s = stops.get(i);
            RetimedStop t = retimed.stops().get(r++);
            result.add(new ScheduledStop(s.type(), s.attraction(), s.meal(), t.arrival(), t.departure(),
                    s.stayMinutes(), t.waitMinutes(), t.travelIn()));
        }
        log.info("[Cafe] DAY{} {} {}({})", day.dayNumber(), result.get(insertAt).arrival(),
                chosen.restaurant().getName(), chosen.restaurant().getGenre());
        return new ScheduledDay(day.dayNumber(), day.date(), day.zone(), List.copyOf(result), day.dropped(), day.warnings());
    }

    /** 끼워 넣을 자리 (그 앞 항목을 떠나는 시각이 earliest~latest 사이, preferred에 가장 가까운 곳). 없으면 -1 */
    int insertPosition(List<ScheduledStop> stops, int lunch, int dinner) {
        int best = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int k = lunch + 1; k <= dinner; k++) {
            int leave = minutes(stops.get(k - 1).departure());
            if (leave < props.getEarliestMinutes() || leave > props.getLatestMinutes()) continue;
            int gap = Math.abs(leave - props.getPreferredMinutes());
            if (gap < bestGap) {
                bestGap = gap;
                best = k;
            }
        }
        return best;
    }

    private static int indexOf(List<ScheduledStop> stops, StopType type) {
        for (int i = 0; i < stops.size(); i++) if (stops.get(i).type() == type) return i;
        return -1;
    }

    private static TravelPoint pointOf(ScheduledStop s) {
        if (s.type() == StopType.ATTRACTION) {
            return new TravelPoint(s.name(), s.attraction().lat(), s.attraction().lng());
        }
        var location = s.meal().chosen().restaurant().getLocation();
        return new TravelPoint(s.name(), location.getY(), location.getX());
    }

    private static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }
}
