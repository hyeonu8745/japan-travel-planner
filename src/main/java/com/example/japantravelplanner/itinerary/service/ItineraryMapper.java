package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.DroppedPlace;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.DayWindow;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.ScheduledStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Trip;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 계산된 일정(ScheduledDay)을 저장할 엔티티로 바꾼다. DB 없이 테스트할 수 있게 순수 함수로 둔다 */
public final class ItineraryMapper {

    private ItineraryMapper() {
    }

    /**
     * @param descriptions   날짜 번호 → AI 하루 설명 (없는 날은 설명 없이 저장)
     * @param attractionRef  관광지 id → 관광지 (DB를 조회하지 않는 참조, getReferenceById)
     * @param restaurantRef  식당 id → 식당
     */
    public static Itinerary toEntity(Trip trip, int versionNo, double krwPerYen,
                                     List<ScheduledDay> days, List<DayWindow> windows,
                                     Map<Integer, String> descriptions,
                                     Function<Long, Attraction> attractionRef,
                                     Function<Long, Restaurant> restaurantRef) {
        Itinerary itinerary = Itinerary.create(trip, versionNo, krwPerYen);

        for (int i = 0; i < days.size(); i++) {
            ScheduledDay scheduled = days.get(i);
            DayWindow window = windows.get(i);

            ItineraryDay day = ItineraryDay.create(
                    scheduled.dayNumber(), scheduled.date(), scheduled.zone(),
                    window.isEmpty() ? null : window.start(),
                    window.isEmpty() ? null : window.end(),
                    descriptions.get(scheduled.dayNumber()),
                    scheduled.dropped().stream().map(d -> new DroppedPlace(d.name(), d.reason())).toList(),
                    scheduled.warnings());

            int order = 1;
            for (ScheduledStop stop : scheduled.stops()) {
                day.addStop(toStop(stop, order++, attractionRef, restaurantRef));
            }
            itinerary.addDay(day);
        }
        return itinerary;
    }

    private static ItineraryStop toStop(ScheduledStop stop, int order,
                                        Function<Long, Attraction> attractionRef,
                                        Function<Long, Restaurant> restaurantRef) {
        boolean attraction = stop.type() == StopType.ATTRACTION;
        return ItineraryStop.builder()
                .stopOrder(order)
                .type(stop.type())
                .attraction(attraction ? attractionRef.apply(stop.attraction().attraction().getId()) : null)
                .restaurant(attraction ? null : restaurantRef.apply(stop.meal().chosen().restaurant().getId()))
                .arrivalTime(stop.arrival())
                .departureTime(stop.departure())
                .stayMinutes(stop.stayMinutes())
                .waitMinutes(stop.waitMinutes())
                .travelMode(stop.travelIn().mode())
                .travelMinutes(stop.travelIn().minutes())
                .travelDistanceMeters(stop.travelIn().distanceMeters())
                .travelEstimated(stop.travelIn().estimated())
                .budgetRelaxed(!attraction && stop.meal().chosen().budgetRelaxed())
                .genreRepeated(!attraction && stop.meal().genreRepeated())
                .build();
    }
}