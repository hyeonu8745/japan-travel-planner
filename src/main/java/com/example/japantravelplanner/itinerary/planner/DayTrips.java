package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.DayTripRoute;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 숙소 하나 + 당일치기 (11-22 확장 5단계). 오사카에 묵으면서 교토·나라를 하루씩 다녀오는 식.
 *
 * - 당일치기 날은 첫날·마지막 날이 아닌 가운데 날에 고르게 놓는다 (첫날 도착·마지막 날 출발은 숙소 도시에서).
 *   가운데 날보다 도시가 많으면 앞의 도시부터.
 * - 그날은 숙소 → 출발역 → (기차·버스) → 도착역에서 시작해 그 도시 관광지를 돌고 도착역으로 돌아온다.
 *   시간대는 아침 8:30에 숙소를 나선다고 보고 이동 시간만큼 늦게 시작해서, 돌아갈 시간만큼 일찍 끝낸다.
 * - 하루가 당일치기인지는 그날 지역(TripZone)의 도시가 숙소 도시와 다른지로 안다 (일정에 따로 저장하지 않는다).
 *   그래서 수정 서비스도 같은 판단으로 도착역을 출발점으로 쓴다 ({@link #startPoint}).
 * - 숙소 ↔ 출발역은 도시 안 이동이라 대표값 {@link #HOTEL_TO_STATION}분으로 둔다.
 */
public final class DayTrips {

    static final int HOTEL_TO_STATION = 20;       // 숙소 ↔ 출발역 (도시 안)
    static final int LEAVE_HOTEL = 8 * 60 + 30;   // 당일치기 날 숙소를 나서는 시각

    /** 정적 메서드만 쓴다 */
    private DayTrips() {
    }

    /**
     * 당일치기 날 배정
     * @param skipped 배정하지 못한 도시와 이유 (숙소 도시에서 갈 수 없음, 날짜가 모자람)
     */
    public record Assignment(Map<Integer, City> byDay, List<String> skipped) {
        /** 당일치기 날 수 */
        public int count() {
            return byDay.size();
        }
    }

    /** 여행 조건의 당일치기 도시를 날짜에 놓는다 (날 번호 → 도시) */
    public static Assignment assign(Trip trip, int days) {
        City base = trip.getBaseCity();
        List<City> reachable = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (City c : trip.getDayTripCities()) {
            if (c == base) continue;
            if (DayTripRoute.between(base, c).isPresent()) {
                reachable.add(c);
            } else {
                skipped.add(topic(c.label()) + " 숙소가 있는 " + base.label() + "에서 당일치기로 다녀오기 어려워 넣지 않았어요.");
            }
        }
        int middle = Math.max(0, days - 2);
        int count = Math.min(reachable.size(), middle);
        for (City c : reachable.subList(count, reachable.size())) {
            skipped.add("여행 날짜가 짧아 " + c.label() + " 당일치기는 넣지 못했어요. (첫날·마지막 날은 숙소 도시에서 보내요)");
        }
        Map<Integer, City> byDay = new LinkedHashMap<>();
        List<Integer> positions = positions(days, count);
        for (int i = 0; i < count; i++) byDay.put(positions.get(i), reachable.get(i));
        return new Assignment(byDay, List.copyOf(skipped));
    }

    /**
     * 당일치기 날 번호: 가운데 날(2 ~ days-1)에 고르게.
     * 예: 4일 여행 1곳 → 3일차, 5일 여행 2곳 → 2·4일차, 4일 여행 2곳 → 2·3일차
     */
    static List<Integer> positions(int days, int count) {
        int middle = days - 2;
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(2 + (int) Math.floor((i + 0.5) * middle / count));
        }
        return result;
    }

    /** 그날 지역이 숙소 도시가 아닌 당일치기 도시면 그 경로 */
    public static Optional<DayTripRoute> routeOf(Trip trip, TripZone zone) {
        if (trip == null || zone == null || zone.getCity() == trip.getBaseCity()) return Optional.empty();
        return DayTripRoute.between(trip.getBaseCity(), zone.getCity());
    }

    /** 그날 일정의 출발·도착 지점: 당일치기 날이면 도착역, 아니면 숙소 */
    public static TravelPoint startPoint(Trip trip, TripZone zone, TravelPoint hotel) {
        return routeOf(trip, zone).map(r -> station(r, zone.getCity())).orElse(hotel);
    }

    /** 당일치기 도시 쪽 역 */
    public static TravelPoint station(DayTripRoute route, City target) {
        DayTripRoute.Station s = route.stationOf(target);
        return new TravelPoint(s.name(), s.lat(), s.lng());
    }

    /** 당일치기 날 저녁을 기다리는 시간이 이보다 길면 그 도시 저녁을 빼고 돌아간다 */
    static final int MAX_DINNER_WAIT = 60;

    /** 저녁 식당에 도착해서 문 열 때(18:00)까지 MAX_DINNER_WAIT 분보다 오래 기다리는지 */
    public static boolean longDinnerWait(ScheduledDay day) {
        return day.stops().stream()
                .anyMatch(s -> s.type() == StopType.DINNER && s.waitMinutes() > MAX_DINNER_WAIT);
    }

    /** 숙소 ↔ 도착역 편도 시간 (숙소 → 출발역 + 기차·버스) */
    public static int oneWayMinutes(DayTripRoute route) {
        return HOTEL_TO_STATION + route.minutes();
    }

    /**
     * 당일치기 날 시간대: 숙소를 8:30에 나서 도착역에서 시작하고(원래 시작보다 이르지 않게),
     * 원래 끝 시각에 숙소에 돌아오도록 그만큼 일찍 끝낸다
     */
    public static DayWindow window(DayWindow base, DayTripRoute route) {
        if (base == null || base.isEmpty()) return base;
        int travel = oneWayMinutes(route);
        int start = Math.max(minutes(base.start()), LEAVE_HOTEL + travel);
        int end = minutes(base.end()) - travel;
        return DayWindow.between(start, end);
    }

    /**
     * 그날 안내 문장: "교토 당일치기: 숙소 → 오사카역 → JR 신쾌속 약 28분 → 교토역 (편도 580엔). …"
     * @param dinner 그날 저녁을 넣을 수 있는지 (먼 곳은 돌아올 시간 때문에 저녁 전에 끝난다)
     */
    public static String note(City base, City target, DayTripRoute route, boolean dinner) {
        String note = target.label() + " 당일치기: 숙소 → " + route.stationOf(base).name() + " → " + route.label()
                + " 약 " + route.minutes() + "분 → " + route.stationOf(target).name()
                + " (편도 " + String.format("%,d", route.fareYen()) + "엔, 1인). 오가는 데 하루 약 "
                + AirportPlan.hours(oneWayMinutes(route) * 2) + "이 걸려요.";
        if (route.label().contains("예약")) note += " 고속버스는 미리 예약해 두세요.";
        if (!dinner) note += " 저녁은 " + base.label() + "에 돌아가서 드세요.";
        return note;
    }

    /**
     * 그날 일정표 앞뒤에 붙는 이동 단계 (응답에서 계산, 공항 단계와 같은 모양)
     * @param windowStart 그날 관광 시작 시각, lastDeparture 마지막 장소를 떠나는 시각 (없으면 그날 끝 시각)
     */
    public static List<AirportPlan.Step> steps(Trip trip, TripZone zone, LocalTime windowStart, LocalTime lastDeparture) {
        Optional<DayTripRoute> found = routeOf(trip, zone);
        if (found.isEmpty() || windowStart == null) return List.of();
        DayTripRoute route = found.get();
        City base = trip.getBaseCity();
        City target = zone.getCity();
        String ride = route.label() + " 약 " + route.minutes() + "분, 편도 " + String.format("%,d", route.fareYen()) + "엔";
        List<AirportPlan.Step> steps = new ArrayList<>();
        steps.add(new AirportPlan.Step(AirportPlan.time(minutes(windowStart) - oneWayMinutes(route)), "DAY_TRIP_GO",
                "숙소 → " + route.stationOf(base).name() + " → " + route.stationOf(target).name() + " (" + ride + ")", "BEFORE"));
        if (lastDeparture != null) {
            steps.add(new AirportPlan.Step(lastDeparture, "DAY_TRIP_BACK",
                    route.stationOf(target).name() + " → " + route.stationOf(base).name() + " → 숙소 (" + ride + ")", "AFTER"));
        }
        return steps;
    }

    /** "교토" → "교토는", "후쿠오카" → "후쿠오카는", "도쿄역" → "도쿄역은" (받침에 따라) */
    static String topic(String word) {
        char last = word.charAt(word.length() - 1);
        boolean batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
        return word + (batchim ? "은" : "는");
    }

    /** 시각 → 하루 분 */
    private static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }
}
