package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 날짜별 지역을 정하고, 지역마다 관광지 후보를 뽑는다.
 * - 지역 점수 = 그 지역 상위 관광지(하루 방문 수만큼) 점수 합
 * - 대화에서 말한 지역(SCHEDULE·ETC 조건에 지역 이름)은 꼭 포함
 * - 오다이바는 야경·쇼핑 관심사가 없으면 점수 절반 (관광지가 적어서)
 * - 첫날·마지막 날은 숙소에서 가까운 지역
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayZonePlanner {

    // 대화에서 지역을 알아보기 위한 별칭
    private static final Map<TripZone, List<String>> ALIASES = Map.of(
            TripZone.ASAKUSA_UENO, List.of("아사쿠사", "우에노", "야나카", "센소지"),
            TripZone.GINZA_TOKYO, List.of("긴자", "도쿄역", "마루노우치", "니혼바시", "쓰키지", "쓰키시마", "황궁"),
            TripZone.AKIHABARA_KANDA, List.of("아키하바라", "간다", "진보초", "구단시타", "야스쿠니"),
            TripZone.SHINJUKU, List.of("신주쿠", "가부키초", "도청"),
            TripZone.SHIBUYA_HARAJUKU, List.of("시부야", "하라주쿠", "오모테산도", "메이지", "요요기"),
            TripZone.ROPPONGI_TOWER, List.of("롯폰기", "도쿄타워", "도쿄 타워", "아자부"),
            TripZone.ODAIBA, List.of("오다이바", "레인보우")
    );

    private final CandidateProperties props;

    public List<DayZone> plan(List<ScoredAttraction> scored, LocalDate startDate, int days, int perDay,
                              Collection<Interest> interests, List<PreferenceConstraint> constraints,
                              double hotelLat, double hotelLng) {

        Map<TripZone, List<ScoredAttraction>> byZone = scored.stream()
                .collect(Collectors.groupingBy(ScoredAttraction::zone, () -> new EnumMap<>(TripZone.class),
                        Collectors.toList()));

        // ① 지역 점수
        Map<TripZone, Double> zoneScore = new EnumMap<>(TripZone.class);
        for (var entry : byZone.entrySet()) {
            double sum = entry.getValue().stream().limit(perDay).mapToDouble(ScoredAttraction::score).sum();
            if (entry.getKey() == TripZone.ODAIBA
                    && !interests.contains(Interest.NIGHT_VIEW) && !interests.contains(Interest.SHOPPING)) {
                sum *= 0.5;
            }
            zoneScore.put(entry.getKey(), sum);
        }

        // ② 대화에서 말한 지역은 먼저, 나머지는 점수순
        Set<TripZone> requested = requestedZones(constraints);
        List<TripZone> ordered = new ArrayList<>();
        requested.stream().filter(byZone::containsKey).forEach(ordered::add);
        zoneScore.entrySet().stream()
                .sorted(Map.Entry.<TripZone, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .filter(zone -> !ordered.contains(zone))
                .forEach(ordered::add);

        // 여행이 지역 수보다 길면 점수 높은 지역부터 다시 (남은 관광지로)
        List<TripZone> selected = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            selected.add(ordered.get(i % ordered.size()));
        }

        // ③ 첫날·마지막 날은 숙소에서 가까운 지역
        List<TripZone> byDistance = new ArrayList<>(selected);
        byDistance.sort(Comparator.comparingDouble(zone -> zone.distanceTo(hotelLat, hotelLng)));
        List<TripZone> dayOrder = arrangeDays(byDistance);

        // ④ 날짜마다 후보 (이미 고른 관광지는 제외)
        Set<Long> used = new HashSet<>();
        List<DayZone> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            TripZone zone = dayOrder.get(i);
            List<ScoredAttraction> candidates = byZone.getOrDefault(zone, List.of()).stream()
                    .filter(c -> !used.contains(c.attraction().getId()))
                    .limit(perDay + props.getExtraCandidates())
                    .toList();
            candidates.stream().limit(perDay).forEach(c -> used.add(c.attraction().getId()));
            result.add(new DayZone(i + 1, startDate.plusDays(i), zone, candidates));
        }

        log.info("[DayZone] 요청 지역 {}, 배정 {}", requested,
                result.stream().map(d -> d.zone().getLabel()).toList());
        return result;
    }

    /** 가까운 순 목록에서 1번째는 첫날, 2번째는 마지막 날, 나머지는 가운데 */
    private List<TripZone> arrangeDays(List<TripZone> byDistance) {
        int n = byDistance.size();
        if (n <= 1) return byDistance;
        TripZone[] days = new TripZone[n];
        days[0] = byDistance.get(0);
        days[n - 1] = byDistance.get(1);
        for (int i = 2; i < n; i++) {
            days[i - 1] = byDistance.get(i);
        }
        return Arrays.asList(days);
    }

    private Set<TripZone> requestedZones(List<PreferenceConstraint> constraints) {
        Set<TripZone> result = new LinkedHashSet<>();
        if (constraints == null) return result;
        for (PreferenceConstraint c : constraints) {
            if (c.getCategory() != ConstraintCategory.SCHEDULE && c.getCategory() != ConstraintCategory.ETC) continue;
            for (var entry : ALIASES.entrySet()) {
                if (entry.getValue().stream().anyMatch(alias -> c.getDescription().contains(alias))) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }
}