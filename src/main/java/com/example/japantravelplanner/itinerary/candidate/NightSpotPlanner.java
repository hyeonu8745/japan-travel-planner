package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.itinerary.candidate.NightSpots.Kind;
import com.example.japantravelplanner.itinerary.planner.DayWindow;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.util.*;

/**
 * 여행 전체를 보고 날마다 저녁 뒤 야경 장소를 정한다 (야경 관심사일 때만).
 *  - 그날 지역의 대표 야경 명소(NightSpots)만 넣는다. 없으면 그날은 야경 없이 저녁으로 끝난다.
 *  - 같은 종류는 여행 전체에서 MAX_PER_KIND번까지. 아직 안 나온 종류부터 채운다 (전망대만 반복되지 않게)
 *  - 같은 조건이면 점수 높은 곳부터
 *  - 저녁 전에 하루가 시작하는 날만 (첫날 늦게 도착하거나 마지막 날 저녁이 없으면 넣어도 빠지기만 한다)
 * 날짜 순서대로 고르면 앞 날짜가 좋은 곳을 먼저 가져가서, 모든 (날짜, 장소) 쌍을 보고 하나씩 배정한다.
 */
@Slf4j
public final class NightSpotPlanner {

    static final int MAX_PER_KIND = 2;
    /** 이 시각보다 늦게 시작하는 날은 저녁 뒤 야경까지 넣을 시간이 없다 */
    static final LocalTime LATEST_START = LocalTime.of(18, 0);

    private NightSpotPlanner() {
    }

    /**
     * @param days    날짜별 지역 (DayZonePlanner 결과)
     * @param scored  점수를 매긴 전체 관광지 (AttractionScorer 결과)
     * @param windows days와 같은 순서의 하루 가능 시간대
     * @return 날짜 번호 → 야경 장소. 야경이 없는 날은 들어 있지 않다
     */
    public static Map<Integer, ScoredAttraction> assign(List<DayZone> days, List<ScoredAttraction> scored,
                                                        List<DayWindow> windows) {
        List<ScoredAttraction> spots = scored.stream()
                .filter(s -> NightSpots.isRepresentative(s.attraction()))
                .toList();

        Map<Integer, ScoredAttraction> result = new LinkedHashMap<>();
        Set<Long> usedSpots = new HashSet<>();
        Map<Kind, Integer> kindCount = new EnumMap<>(Kind.class);

        while (true) {
            DayZone bestDay = null;
            ScoredAttraction bestSpot = null;
            int bestCount = Integer.MAX_VALUE;
            for (int i = 0; i < days.size(); i++) {
                DayZone day = days.get(i);
                if (result.containsKey(day.dayNumber()) || !eligible(windows.get(i))) continue;
                for (ScoredAttraction spot : spots) {
                    if (spot.zone() != day.zone() || usedSpots.contains(spot.attraction().getId())) continue;
                    int count = kindCount.getOrDefault(NightSpots.kindOf(spot.attraction()), 0);
                    if (count >= MAX_PER_KIND) continue;
                    if (count < bestCount || (count == bestCount && spot.score() > bestSpot.score())) {
                        bestDay = day;
                        bestSpot = spot;
                        bestCount = count;
                    }
                }
            }
            if (bestSpot == null) break;
            result.put(bestDay.dayNumber(), bestSpot);
            usedSpots.add(bestSpot.attraction().getId());
            kindCount.merge(NightSpots.kindOf(bestSpot.attraction()), 1, Integer::sum);
        }

        log.info("[Night] {}", result.entrySet().stream()
                .map(e -> "DAY" + e.getKey() + " " + e.getValue().attraction().getName()).toList());
        return result;
    }

    static boolean eligible(DayWindow window) {
        return window != null && !window.isEmpty() && window.dinner() && !window.start().isAfter(LATEST_START);
    }
}
