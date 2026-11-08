package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimedStop;
import com.example.japantravelplanner.restaurant.entity.Restaurant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 새 버전의 하루를 만드는 공통 조각 (11-08 리팩터링: 수정 서비스 3곳에 복사돼 있던 것).
 * 바뀌지 않은 날 복사, 다시 계산한 시각으로 항목 만들기, "같은 음식" 표시 다시 판단, 시간 안내 구분.
 */
public final class DayCopies {

    private DayCopies() {
    }

    /**
     * 바뀌지 않은 날: 그대로 복사한다. 끼니의 "같은 음식" 표시만 repeated 값으로 (없으면 원래 값).
     * repeated 의 열쇠는 원래 항목(ItineraryStop)이다.
     */
    public static ItineraryDay copyDay(ItineraryDay day, Map<?, Boolean> repeated) {
        ItineraryDay copy = emptyCopy(day, day.getDescription(), new ArrayList<>(day.getDropped()),
                new ArrayList<>(day.getWarnings()));
        for (ItineraryStop s : day.getStops()) {
            copy.addStop(sameAs(s)
                    .arrivalTime(s.getArrivalTime())
                    .departureTime(s.getDepartureTime())
                    .waitMinutes(s.getWaitMinutes())
                    .travelMode(s.getTravelMode())
                    .travelMinutes(s.getTravelMinutes())
                    .travelDistanceMeters(s.getTravelDistanceMeters())
                    .travelEstimated(s.isTravelEstimated())
                    .genreRepeated(repeated.getOrDefault(s, s.isGenreRepeated()))
                    .build());
        }
        return copy;
    }

    /** 항목 없는 같은 날 (번호·날짜·지역·시간대는 그대로) */
    public static ItineraryDay emptyCopy(ItineraryDay day, String description, List<DroppedPlace> dropped,
                                         List<String> warnings) {
        return ItineraryDay.create(day.getDayNumber(), day.getDate(), day.getZone(),
                day.getWindowStart(), day.getWindowEnd(), description, dropped, warnings);
    }

    /** 원래 항목과 같은 장소·순서·체류·예산 표시·대안 (시각·이동은 호출하는 쪽이 채운다) */
    public static ItineraryStop.ItineraryStopBuilder sameAs(ItineraryStop s) {
        return ItineraryStop.builder()
                .stopOrder(s.getStopOrder())
                .type(s.getType())
                .attraction(s.getAttraction())
                .restaurant(s.getRestaurant())
                .stayMinutes(s.getStayMinutes())
                .budgetRelaxed(s.isBudgetRelaxed())
                .genreRepeated(s.isGenreRepeated())
                .alternativeRestaurantIds(s.getAlternativeRestaurantIds());
    }

    /** 다시 계산한 시각·이동을 채운다 */
    public static ItineraryStop.ItineraryStopBuilder timed(ItineraryStop.ItineraryStopBuilder builder, RetimedStop timed) {
        return builder
                .arrivalTime(timed.arrival())
                .departureTime(timed.departure())
                .waitMinutes(timed.waitMinutes())
                .travelMode(timed.travelIn().mode())
                .travelMinutes(timed.travelIn().minutes())
                .travelDistanceMeters(timed.travelIn().distanceMeters())
                .travelEstimated(timed.travelIn().estimated());
    }

    /**
     * 모든 끼니의 "바로 앞 끼니와 같은 음식" 표시를 다시 판단한다 (대표 장르가 겹치면 true).
     * 일정을 만들 때와 같은 기준이고, 날짜를 넘어서 이어진다. 끼니를 바꾸거나 지우거나 옮기면 앞 끼니가 달라진다.
     *
     * @param editedDay   바뀐 날 (없으면 null)
     * @param editedKeys  바뀐 날의 새 순서 열쇠들 (항목 또는 자리). 나머지 날은 기존 항목이 열쇠다
     * @param restaurantOf 열쇠의 식당 (관광지면 null)
     */
    public static Map<Object, Boolean> genreRepeatedFlags(Itinerary itinerary, ItineraryDay editedDay,
                                                          List<?> editedKeys, Function<Object, Restaurant> restaurantOf) {
        Map<Object, Boolean> flags = new IdentityHashMap<>();
        Set<String> previous = Set.of();
        for (ItineraryDay day : itinerary.getDays()) {
            List<?> keys = day == editedDay ? editedKeys : day.getStops();
            for (Object key : keys) {
                Restaurant r = restaurantOf.apply(key);
                if (r == null) {
                    continue;
                }
                Set<String> main = RestaurantFinder.mainGenres(r.getGenre());
                flags.put(key, !Collections.disjoint(main, previous));
                previous = main;
            }
        }
        return flags;
    }

    /** 시각을 다시 계산하면 새로 만들어지는 안내 (점심·저녁 시간, 하루 넘침) */
    public static boolean isTimeWarning(String warning) {
        return warning.startsWith("점심 시간이") || warning.startsWith("저녁 시간이") || warning.startsWith("일정이 ");
    }
}
