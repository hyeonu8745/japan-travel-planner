package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.global.util.GeoUtils;
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
 * - 하루에 두 지역 (10-11 사용자 결정): 중심 지역에서 이동 범위(가까운 곳 위주 7km, 대중교통 10km, 렌터카 12km) 안의
 *   이웃 지역 하나를 함께 돌고, 두 지역 관광지를 섞어서 후보로 쓴다 (예: 아사쿠사+아키하바라, 롯폰기+시부야)
 * - 날짜마다 후보: 기본은 지역 안에서 종류·위치를 섞어서 고른다 (같은 종류·500m 안이면 감점).
 *   가까운 곳 위주 여행(TravelOptions.compact)은 예전처럼 점수순
 *   (10-10 사용자 결정: 점수순으로만 고르면 浜離宮恩賜庭園 + 그 안의 三百年の松처럼 주변만 돌았다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayZonePlanner {

    // 대화에서 지역을 알아보기 위한 별칭
    private static final Map<TripZone, List<String>> ALIASES = Map.of(
            TripZone.ASAKUSA_UENO, List.of("아사쿠사", "우에노", "야나카", "센소지"),
            TripZone.GINZA_TOKYO, List.of("긴자", "도쿄역", "마루노우치", "니혼바시", "쓰키지", "쓰키시마", "황궁"),
            TripZone.AKIHABARA_KANDA, List.of("아키하바라", "간다", "진보초", "구단시타", "야스쿠니",
                    "피규어", "프라모델", "건프라", "애니"),   // 피규어·애니 숍이 모인 곳 (Wish.HOBBY)
            TripZone.SHINJUKU, List.of("신주쿠", "가부키초", "도청"),
            TripZone.SHIBUYA_HARAJUKU, List.of("시부야", "하라주쿠", "오모테산도", "메이지", "요요기"),
            TripZone.ROPPONGI_TOWER, List.of("롯폰기", "도쿄타워", "도쿄 타워", "아자부"),
            TripZone.ODAIBA, List.of("오다이바", "레인보우")
    );

    private final CandidateProperties props;

    /**
     * @param compact true면 가까운 곳 위주 (후보를 점수순으로만), false면 지역 안에서 종류·위치를 섞는다
     */
    public List<DayZone> plan(List<ScoredAttraction> scored, LocalDate startDate, int days, int perDay,
                              Collection<Interest> interests, List<PreferenceConstraint> constraints,
                              double hotelLat, double hotelLng, boolean compact) {
        return plan(scored, startDate, days, perDay, interests, constraints, hotelLat, hotelLng, compact, 0);
    }

    /**
     * @param rangeMeters 하루 이동 범위. 0보다 크면 중심 지역에서 이 거리 안의 이웃 지역 하나를 함께 돈다 (rangeMeters(...))
     */
    public List<DayZone> plan(List<ScoredAttraction> scored, LocalDate startDate, int days, int perDay,
                              Collection<Interest> interests, List<PreferenceConstraint> constraints,
                              double hotelLat, double hotelLng, boolean compact, double rangeMeters) {
        return plan(scored, startDate, days, perDay, interests, constraints, hotelLat, hotelLng, compact, rangeMeters, false);
    }

    /**
     * @param lastDayNearHotel true면 마지막 날은 숙소 근처 쇼핑 (10-15, 비행기 출발 시각이 있을 때):
     *                         숙소가 있는 지역, 이웃 지역 없이, 숙소에서 lastDayRadiusMeters 안 관광지, 쇼핑 가산, 놀이공원 등 제외
     */
    public List<DayZone> plan(List<ScoredAttraction> scored, LocalDate startDate, int days, int perDay,
                              Collection<Interest> interests, List<PreferenceConstraint> constraints,
                              double hotelLat, double hotelLng, boolean compact, double rangeMeters,
                              boolean lastDayNearHotel) {
        boolean nearHotelLast = lastDayNearHotel && days >= 2;

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
        // 마지막 날이 숙소 근처 쇼핑이면: 나머지 날로 같은 규칙, 마지막 날은 숙소가 있는 지역 (첫날과 같아도 된다)
        List<TripZone> byDistance = new ArrayList<>(nearHotelLast ? selected.subList(0, days - 1) : selected);
        byDistance.sort(Comparator.comparingDouble(zone -> zone.distanceTo(hotelLat, hotelLng)));
        List<TripZone> dayOrder = new ArrayList<>(arrangeDays(byDistance));
        if (nearHotelLast) {
            dayOrder.add(hotelZone(hotelLat, hotelLng));
        }

        // ④ 날짜마다 후보 (이미 고른 관광지는 제외)
        Set<Long> used = new HashSet<>();
        Set<String> usedBrands = new HashSet<>();   // 같은 가게의 다른 지점은 여행 전체에서 한 번 (BrandKey)
        Set<TripZone> primaries = EnumSet.copyOf(dayOrder);
        Map<TripZone, Integer> partnerUses = new EnumMap<>(TripZone.class);
        List<DayZone> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            TripZone zone = dayOrder.get(i);
            if (nearHotelLast && i == days - 1) {
                result.add(new DayZone(i + 1, startDate.plusDays(i), zone,
                        lastDayCandidates(scored, byZone.getOrDefault(zone, List.of()), used, usedBrands,
                                hotelLat, hotelLng, perDay + props.getExtraCandidates()), null));
                continue;
            }
            TripZone partner = rangeMeters > 0
                    ? choosePartner(zone, byZone, used, primaries, partnerUses, perDay, interests, rangeMeters)
                    : null;
            if (partner != null) partnerUses.merge(partner, 1, Integer::sum);

            List<ScoredAttraction> zonePool = new ArrayList<>(byZone.getOrDefault(zone, List.of()));
            if (partner != null) {
                byZone.getOrDefault(partner, List.of()).stream()
                        .filter(c -> zone.distanceTo(c.lat(), c.lng()) <= rangeMeters)
                        .forEach(zonePool::add);
                zonePool.sort(Comparator.comparingDouble(ScoredAttraction::score).reversed());
            }
            Map<String, ScoredAttraction> byBrand = new LinkedHashMap<>();   // 점수 높은 지점 하나만
            zonePool.stream()
                    .filter(c -> !used.contains(c.attraction().getId()))
                    .filter(c -> !usedBrands.contains(BrandKey.of(c.attraction().getName())))
                    .forEach(c -> byBrand.putIfAbsent(BrandKey.of(c.attraction().getName()), c));
            List<ScoredAttraction> pool = List.copyOf(byBrand.values());
            int count = perDay + props.getExtraCandidates();
            List<ScoredAttraction> candidates = compact
                    ? pool.stream().limit(count).toList()
                    : diverse(pool, count);
            candidates.stream().limit(perDay).forEach(c -> {
                used.add(c.attraction().getId());
                usedBrands.add(BrandKey.of(c.attraction().getName()));
            });
            result.add(new DayZone(i + 1, startDate.plusDays(i), zone, candidates, partner));
        }

        log.info("[DayZone] 요청 지역 {}, 배정 {}", requested, result.stream()
                .map(d -> d.partner() == null ? d.zone().getLabel() : d.zone().getLabel() + "+" + d.partner().getLabel())
                .toList());
        return result;
    }

    /**
     * 그날 함께 돌 이웃 지역. 중심 사이 거리가 rangeMeters 안인 지역 중
     * 값 = 아직 안 쓴 상위 관광지(하루 방문 수의 절반) 점수 합 - 거리(km) × partnerDistancePenaltyPerKm
     *      - (다른 날의 중심 지역이면 partnerReusePenalty) - (이미 함께 돈 횟수 × partnerReusePenalty)
     * 가 가장 큰 곳. 오다이바는 지역 점수와 같은 이유로 야경·쇼핑 관심사가 없으면 점수 절반. 남은 관광지가 없으면 null
     */
    TripZone choosePartner(TripZone zone, Map<TripZone, List<ScoredAttraction>> byZone, Set<Long> used,
                           Set<TripZone> primaries, Map<TripZone, Integer> partnerUses, int perDay,
                           Collection<Interest> interests, double rangeMeters) {
        int take = Math.max(1, (perDay + 1) / 2);
        TripZone best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (TripZone other : TripZone.values()) {
            if (other == zone || zone.distanceTo(other) > rangeMeters) continue;
            double sum = byZone.getOrDefault(other, List.of()).stream()
                    .filter(c -> !used.contains(c.attraction().getId()))
                    .filter(c -> zone.distanceTo(c.lat(), c.lng()) <= rangeMeters)
                    .limit(take)
                    .mapToDouble(ScoredAttraction::score).sum();
            if (sum == 0) continue;
            if (other == TripZone.ODAIBA
                    && !interests.contains(Interest.NIGHT_VIEW) && !interests.contains(Interest.SHOPPING)) {
                sum *= 0.5;
            }
            double value = sum - zone.distanceTo(other) / 1000 * props.getPartnerDistancePenaltyPerKm()
                    - (primaries.contains(other) ? props.getPartnerReusePenalty() : 0)
                    - partnerUses.getOrDefault(other, 0) * props.getPartnerReusePenalty();
            if (value > bestValue) {
                best = other;
                bestValue = value;
            }
        }
        return best;
    }

    /** 이동수단·여행 성향에 따른 하루 이동 범위(미터) */
    public double rangeMeters(boolean compact, boolean rentalCar) {
        double km = compact ? props.getDayRangeCompactKm()
                : rentalCar ? props.getDayRangeCarKm() : props.getDayRangeTransitKm();
        return km * 1000;
    }

    /**
     * 점수가 높으면서 이미 고른 곳과 종류·위치가 겹치지 않는 곳부터 하나씩 고른다.
     * 조정 점수 = 점수 - 같은 종류 수 × sameKindPenalty - (nearMeters 안에 고른 곳이 있으면 nearPenalty)
     * 조정 점수가 같으면 원래 점수가 높은 곳(앞에 있는 곳)
     *
     * @param pool 점수 높은 순
     */
    List<ScoredAttraction> diverse(List<ScoredAttraction> pool, int count) {
        List<ScoredAttraction> picked = new ArrayList<>();
        List<ScoredAttraction> rest = new ArrayList<>(pool);
        while (picked.size() < count && !rest.isEmpty()) {
            ScoredAttraction best = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (ScoredAttraction c : rest) {
                double value = adjustedScore(c, picked);
                if (value > bestValue) {
                    best = c;
                    bestValue = value;
                }
            }
            picked.add(best);
            rest.remove(best);
        }
        return List.copyOf(picked);
    }

    double adjustedScore(ScoredAttraction candidate, List<ScoredAttraction> picked) {
        PlaceKind kind = PlaceKind.of(candidate.attraction());
        long sameKind = kind == PlaceKind.OTHER ? 0
                : picked.stream().filter(p -> PlaceKind.of(p.attraction()) == kind).count();
        boolean near = picked.stream().anyMatch(p ->
                GeoUtils.distanceMeters(p.lat(), p.lng(), candidate.lat(), candidate.lng()) <= props.getNearMeters());
        return candidate.score() - sameKind * props.getSameKindPenalty() - (near ? props.getNearPenalty() : 0);
    }

    /** 숙소가 있는 지역 (지역 중심이 가장 가까운 곳) */
    static TripZone hotelZone(double hotelLat, double hotelLng) {
        return Arrays.stream(TripZone.values())
                .min(Comparator.comparingDouble(zone -> zone.distanceTo(hotelLat, hotelLng)))
                .orElseThrow();
    }

    /**
     * 마지막 날 후보: 숙소에서 lastDayRadiusMeters 안 (지역과 상관없이), 앞 날에 고른 곳·같은 가게 제외,
     * 놀이공원·동물원·수족관 제외(짐 찾으러 돌아갈 시간 전에 끝내기 어렵다), 쇼핑은 가산.
     * 숙소 근처에 모자라면 숙소 지역의 나머지 관광지로 채운다
     */
    List<ScoredAttraction> lastDayCandidates(List<ScoredAttraction> scored, List<ScoredAttraction> zonePool,
                                             Set<Long> used, Set<String> usedBrands,
                                             double hotelLat, double hotelLng, int count) {
        Comparator<ScoredAttraction> byLastDayScore = Comparator.comparingDouble(this::lastDayScore).reversed();
        Map<String, ScoredAttraction> byBrand = new LinkedHashMap<>();
        scored.stream()
                .filter(c -> GeoUtils.distanceMeters(hotelLat, hotelLng, c.lat(), c.lng()) <= props.getLastDayRadiusMeters())
                .filter(c -> PlaceKind.of(c.attraction()) != PlaceKind.AMUSEMENT)
                .sorted(byLastDayScore)
                .filter(c -> !used.contains(c.attraction().getId()))
                .filter(c -> !usedBrands.contains(BrandKey.of(c.attraction().getName())))
                .forEach(c -> byBrand.putIfAbsent(BrandKey.of(c.attraction().getName()), c));
        if (byBrand.size() < count) {
            zonePool.stream()
                    .filter(c -> PlaceKind.of(c.attraction()) != PlaceKind.AMUSEMENT)
                    .sorted(Comparator.comparingDouble(ScoredAttraction::score).reversed())
                    .filter(c -> !used.contains(c.attraction().getId()))
                    .filter(c -> !usedBrands.contains(BrandKey.of(c.attraction().getName())))
                    .forEach(c -> byBrand.putIfAbsent(BrandKey.of(c.attraction().getName()), c));
        }
        return byBrand.values().stream().limit(count).toList();
    }

    private double lastDayScore(ScoredAttraction c) {
        return c.score() + (PlaceKind.of(c.attraction()) == PlaceKind.SHOPPING ? props.getLastDayShoppingBonus() : 0);
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