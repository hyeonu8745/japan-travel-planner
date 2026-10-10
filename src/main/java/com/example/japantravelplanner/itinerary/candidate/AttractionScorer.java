package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.itinerary.zone.ZoneAssigner;
import com.example.japantravelplanner.trip.entity.Interest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 관광지마다 점수를 매기고 지역을 배정한다.
 * 점수 = 보정 평점 + 관심사 보너스 (관심사 하나당, 최대 2개까지)
 * 어느 지역에도 속하지 않는 관광지(중심에서 3km 초과)는 제외
 * 같은 장소가 여러 개로 수집된 경우 점수 높은 하나만 남긴다
 *  - 이름이 겹치고 800m 이내 (東京都庁 + 東京都庁第一本庁舎 北展望室)
 *  - 이름과 상관없이 sameSpotMeters(150m) 이내 (愛宕山 + 出世の石段 43m, スクランブル交差点 + ハチ公像 48m)
 * 리뷰가 minReviews(100개) 미만인 관광지도 제외 (なだれ坂 36개처럼 일정에 넣기엔 너무 작은 곳)
 * 야경 관심사면 대표 야경 명소(NightSpots)는 같은 장소 합치기에서 빼지 않는다
 *  - 歌舞伎町一番街アーチ가 137m 옆 新宿東口の猫에 합쳐져 사라져서 신주쿠 날 야경이 비었다 (10-10 여행 8 v9)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttractionScorer {

    private static final double DUPLICATE_DISTANCE_M = 800;

    private final CandidateProperties props;
    private final ZoneAssigner zoneAssigner;

    public List<ScoredAttraction> score(List<Attraction> attractions, Collection<Interest> interests) {
        double mean = attractions.stream()
                .filter(a -> a.getRating() != null)
                .mapToDouble(Attraction::getRating)
                .average()
                .orElse(4.0);

        List<ScoredAttraction> result = new ArrayList<>();
        for (Attraction a : attractions) {
            if (a.getLocation() == null || a.getRating() == null) continue;

            int reviews = a.getReviewCnt() == null ? 0 : a.getReviewCnt();
            if (reviews < props.getMinReviews()) continue;

            double lat = a.getLocation().getY();
            double lng = a.getLocation().getX();
            zoneAssigner.assign(lat, lng).ifPresent(zone -> {
                double score = bayesian(a.getRating(), reviews, mean, props.getPriorReviews())
                        + interestBonus(a, interests);
                result.add(new ScoredAttraction(a, zone, score));
            });
        }
        result.sort(Comparator.comparingDouble(ScoredAttraction::score).reversed());
        boolean keepNightSpots = interests != null && interests.contains(Interest.NIGHT_VIEW);
        return removeNearDuplicates(result, keepNightSpots);
    }

    /** 보정 평점: 리뷰가 적을수록 전체 평균 쪽으로 끌어당김 */
    static double bayesian(double rating, int reviews, double mean, int priorReviews) {
        return (reviews * rating + priorReviews * mean) / (reviews + priorReviews);
    }

    private double interestBonus(Attraction attraction, Collection<Interest> interests) {
        if (interests == null || interests.isEmpty()) return 0;
        long matched = interests.stream()
                .filter(interest -> InterestTypes.matches(interest, attraction))
                .count();
        return Math.min(matched, 2) * props.getInterestBonus();
    }

    /**
     * 점수 높은 순으로 보면서, 이미 남긴 관광지와 같은 장소면 건너뛴다.
     * 예: 東京都庁 + 東京都庁第一本庁舎 北展望室, 新宿御苑 + 新宿御苑 桜園地
     */
    private List<ScoredAttraction> removeNearDuplicates(List<ScoredAttraction> sorted, boolean keepNightSpots) {
        List<ScoredAttraction> kept = new ArrayList<>();
        for (ScoredAttraction candidate : sorted) {
            if (keepNightSpots && NightSpots.isRepresentative(candidate.attraction())) {
                // 밤에 따로 가는 장소라 낮 명소와 가까워도 남긴다. 먼저 남은 같은 이름의 하위 장소는 이쪽으로 합친다
                kept.removeIf(k -> !NightSpots.isRepresentative(k.attraction()) && isNearDuplicate(
                        k.attraction().getName(), k.lat(), k.lng(),
                        candidate.attraction().getName(), candidate.lat(), candidate.lng()));
                kept.add(candidate);
                continue;
            }
            // 남겨둔 대표 야경 명소는 밤에 따로 가는 곳이라, 거리만 가까운 낮 명소(歌舞伎町 아치 옆 新宿東口の猫)는 지우지 않는다.
            // 이름이 겹치는 같은 장소(お台場海浜公園 + お台場海浜公園の展望デッキ)는 지운다 (10-10 v11: 낮·밤에 같은 공원)
            boolean duplicate = kept.stream().anyMatch(k -> {
                boolean nameDuplicate = isNearDuplicate(k.attraction().getName(), k.lat(), k.lng(),
                        candidate.attraction().getName(), candidate.lat(), candidate.lng());
                if (keepNightSpots && NightSpots.isRepresentative(k.attraction())) return nameDuplicate;
                return nameDuplicate
                        || isSameSpot(k.lat(), k.lng(), candidate.lat(), candidate.lng(), props.getSameSpotMeters());
            });
            if (duplicate) {
                log.debug("[Scorer] 중복 제외: {}", candidate.attraction().getName());
            } else {
                kept.add(candidate);
            }
        }
        return kept;
    }

    /**
     * 이름이 달라도 아주 가까우면(기본 150m) 한 장소 안의 하위 명소로 본다.
     * 점수 높은 순으로 보면서 이미 남긴 것과만 비교하므로, 줄줄이 이어진 곳이 통째로 합쳐지지는 않는다
     */
    static boolean isSameSpot(double latA, double lngA, double latB, double lngB, double meters) {
        return GeoUtils.distanceMeters(latA, lngA, latB, lngB) <= meters;
    }

    /** 가깝고(300m 이내), 짧은 이름이 긴 이름 안에 들어 있으면 같은 장소로 본다 */
    static boolean isNearDuplicate(String nameA, double latA, double lngA,
                                   String nameB, double latB, double lngB) {
        if (GeoUtils.distanceMeters(latA, lngA, latB, lngB) > DUPLICATE_DISTANCE_M) return false;

        String a = normalize(nameA);
        String b = normalize(nameB);
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        return shorter.length() >= 3 && longer.contains(shorter);
    }

    /** 공백과 괄호 안 내용을 지워서 비교 ("さくら坂 (渋谷)" → "さくら坂") */
    private static String normalize(String name) {
        return name.replaceAll("\\(.*?\\)|（.*?）", "").replaceAll("\\s", "");
    }
}