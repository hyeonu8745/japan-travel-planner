package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.policy.SensitivePlaces;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.entity.AttractionKind;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.itinerary.zone.ZoneAssigner;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.entity.Interest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 관광지마다 점수를 매기고 지역을 배정한다.
 * 점수 = 기본 점수 (11-18 확장 데이터, {@link #baseScore})
 *       + 관심사 보너스 (관심사 하나당, 최대 2개까지) + 대화에서 말한 취향(Wish) 보너스 (한 번)
 *  - 기본 점수 = 기준값 + 평점 보정(보정 평점 - 평균, Google 평점이 있을 때만) + 유명함 + 종류 보정 + 꼭 가는 곳 가산
 *  - 유명함 = max(인기도 가중치 × log10(1 + 위키 언어 수), 리뷰 가중치 × log10(1 + 리뷰 수 / 100))
 *    위키 인기도는 82%가 5 미만이라 구분력이 낮고 "유명함"이지 "가볼 만함"이 아니어서(天智天皇陵), 리뷰 수와 큰 쪽을 쓰고
 *    능·묘·비석은 낮춘다. Google 평점이 있는 곳은 예전처럼 보정 평점이 반영된다
 * 후보 = 위키 인기도 1 이상 또는 꼭 가는 곳 또는 리뷰 minReviews(100개) 이상 또는 대표 야경 명소
 *  (예전 "리뷰 100개 미만 제외"는 Google 평점이 있는 도쿄 데이터 기준이었다). Google 에서 폐업으로 확인한 곳은 뺀다
 * 어느 지역에도 속하지 않는 관광지(중심에서 3km 초과)는 제외
 * 같은 장소가 여러 개로 수집된 경우 점수 높은 하나만 남긴다
 *  - 이름이 겹치고 800m 이내 (東京都庁 + 東京都庁第一本庁舎 北展望室)
 *  - 이름과 상관없이 sameSpotMeters(150m) 이내 (愛宕山 + 出世の石段 43m, スクランブル交差点 + ハチ公像 48m)
 *  - 이름의 恩賜·都立 같은 말은 빼고 비교 (上野恩賜公園 + 上野公園)
 *  - 대표 야경 명소끼리도 같은 장소면 하나만 (東京都庁第一本庁舎 北展望室 + 東京都庁展望室 北)
 * 야경 관심사면 대표 야경 명소(NightSpots)는 같은 장소 합치기에서 빼지 않는다
 *  - 歌舞伎町一番街アーチ가 137m 옆 新宿東口の猫에 합쳐져 사라져서 신주쿠 날 야경이 비는 것을 막는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttractionScorer {

    private static final double DUPLICATE_DISTANCE_M = 800;

    private final CandidateProperties props;
    private final ZoneAssigner zoneAssigner;

    /** 대화 취향 없이 점수 매기기 */
    public List<ScoredAttraction> score(List<Attraction> attractions, Collection<Interest> interests) {
        return score(attractions, interests, Set.of());
    }

    /** 여행 기준 도시의 지역에 들어가는 관광지만 점수 매기기 (일정 생성·교체·추가 후보) */
    public List<ScoredAttraction> score(List<Attraction> attractions, Collection<Interest> interests,
                                        Collection<Wish> wishes, City city) {
        return score(attractions, interests, wishes, zone -> zone.getCity() == city);
    }

    /**
     * @param wishes 대화에서 말한 취향(피규어 숍 등). 맞는 관광지에 관심사와 같은 크기의 가산점을 한 번 준다
     */
    public List<ScoredAttraction> score(List<Attraction> attractions, Collection<Interest> interests,
                                        Collection<Wish> wishes) {
        return score(attractions, interests, wishes, zone -> true);
    }

    private List<ScoredAttraction> score(List<Attraction> attractions, Collection<Interest> interests,
                                         Collection<Wish> wishes, java.util.function.Predicate<TripZone> zoneFilter) {
        double mean = attractions.stream()
                .filter(a -> a.getRating() != null)
                .mapToDouble(Attraction::getRating)
                .average()
                .orElse(4.0);

        List<ScoredAttraction> result = new ArrayList<>();
        for (Attraction a : attractions) {
            if (!isCandidate(a)) continue;

            double lat = a.getLocation().getY();
            double lng = a.getLocation().getX();
            zoneAssigner.assign(lat, lng).filter(zoneFilter).ifPresent(zone -> {
                double score = baseScore(a, mean, props)
                        + interestBonus(a, interests) + wishBonus(a, wishes);
                result.add(new ScoredAttraction(a, zone, score));
            });
        }
        result.sort(Comparator.comparingDouble(ScoredAttraction::score).reversed());
        boolean keepNightSpots = interests != null && interests.contains(Interest.NIGHT_VIEW);
        return removeNearDuplicates(result, keepNightSpots);
    }

    /** 일정 후보가 될 수 있는 관광지인지 (좌표, 민감 장소·폐업 제외, 인기도·꼭 가는 곳·리뷰 수·야경 명소 중 하나) */
    boolean isCandidate(Attraction a) {
        if (a.getLocation() == null) return false;
        if (SensitivePlaces.isExcluded(a.getName())) return false;   // 야스쿠니 등 민감 장소는 추천하지 않는다
        if (a.getGoogleMatchStatus() == GoogleMatchStatus.CLOSED) return false;
        int popularity = a.getPopularity() == null ? 0 : a.getPopularity();
        int reviews = a.getReviewCnt() == null ? 0 : a.getReviewCnt();
        return popularity >= props.getMinPopularity() || a.isMustSee()
                || reviews >= props.getMinReviews() || NightSpots.isRepresentative(a);
    }

    /** 기본 점수 (관심사·취향 가산 전). 클래스 설명 참고 */
    static double baseScore(Attraction a, double meanRating, CandidateProperties props) {
        int reviews = a.getReviewCnt() == null ? 0 : a.getReviewCnt();
        int popularity = a.getPopularity() == null ? 0 : a.getPopularity();
        double quality = a.getRating() == null ? 0
                : bayesian(a.getRating(), reviews, meanRating, props.getPriorReviews()) - meanRating;
        double fame = Math.max(props.getPopularityWeight() * Math.log10(1 + popularity),
                props.getReviewFameWeight() * Math.log10(1 + reviews / 100.0));
        double mustSee = a.isMustSee() ? props.getMustSeeBonus() : 0;
        return props.getBaseScore() + quality + fame + kindAdjust(a) + mustSee;
    }

    /** 이름에 이 말이 있으면 능·묘·비석 같은 곳이라 낮춘다 (위키 인기도가 높아도 일정에 넣을 만하지 않다) */
    private static final List<String> MINOR_NAME_WORDS = List.of("陵", "墓", "碑", "塚", "供養", "跡");

    /** 종류 보정: 성·궁궐은 조금 올리고, 작은 절·신사·사적은 조금 낮춘다 (도쿄에만 절이 748곳) */
    static double kindAdjust(Attraction a) {
        double adjust = 0;
        AttractionKind kind = a.getKind();
        if (kind == AttractionKind.CASTLE) adjust += 0.1;
        if (kind == AttractionKind.TEMPLE || kind == AttractionKind.SHRINE) adjust -= 0.1;
        if (kind == AttractionKind.HISTORIC || kind == AttractionKind.WORSHIP) adjust -= 0.2;
        String name = a.getName() == null ? "" : a.getName();
        if (MINOR_NAME_WORDS.stream().anyMatch(name::contains)) adjust -= 0.3;
        return adjust;
    }

    /** 보정 평점: 리뷰가 적을수록 전체 평균 쪽으로 끌어당김 */
    static double bayesian(double rating, int reviews, double mean, int priorReviews) {
        return (reviews * rating + priorReviews * mean) / (reviews + priorReviews);
    }

    /** 관심사 가산점: 맞는 관심사 수(최대 2개) × 관심사 가산점 */
    private double interestBonus(Attraction attraction, Collection<Interest> interests) {
        if (interests == null || interests.isEmpty()) return 0;
        long matched = interests.stream()
                .filter(interest -> InterestTypes.matches(interest, attraction))
                .count();
        return Math.min(matched, 2) * props.getInterestBonus();
    }

    /** 대화 취향 가산점: 하나라도 맞으면 관심사 하나만큼 (한 번) */
    private double wishBonus(Attraction attraction, Collection<Wish> wishes) {
        if (wishes == null || wishes.isEmpty()) return 0;
        return wishes.stream().anyMatch(w -> w.matches(attraction)) ? props.getInterestBonus() : 0;
    }

    /**
     * 점수 높은 순으로 보면서, 이미 남긴 관광지와 같은 장소면 건너뛴다.
     * 예: 東京都庁 + 東京都庁第一本庁舎 北展望室, 新宿御苑 + 新宿御苑 桜園地
     */
    private List<ScoredAttraction> removeNearDuplicates(List<ScoredAttraction> sorted, boolean keepNightSpots) {
        List<ScoredAttraction> kept = new ArrayList<>();
        for (ScoredAttraction candidate : sorted) {
            if (keepNightSpots && NightSpots.isRepresentative(candidate.attraction())) {
                // 같은 야경 명소가 두 이름으로 들어 있으면(예전 도쿄 데이터 + 확장 데이터) 점수 높은 하나만
                boolean sameNightSpot = kept.stream().anyMatch(k -> NightSpots.isRepresentative(k.attraction())
                        && (isSameSpot(k.lat(), k.lng(), candidate.lat(), candidate.lng(), props.getSameSpotMeters())
                        || isNearDuplicate(k.attraction().getName(), k.lat(), k.lng(),
                        candidate.attraction().getName(), candidate.lat(), candidate.lng())));
                if (sameNightSpot) {
                    log.debug("[Scorer] 같은 야경 명소 제외: {}", candidate.attraction().getName());
                    continue;
                }
                // 밤에 따로 가는 장소라 낮 명소와 가까워도 남긴다. 먼저 남은 같은 이름의 하위 장소는 이쪽으로 합친다
                kept.removeIf(k -> !NightSpots.isRepresentative(k.attraction()) && isNearDuplicate(
                        k.attraction().getName(), k.lat(), k.lng(),
                        candidate.attraction().getName(), candidate.lat(), candidate.lng()));
                kept.add(candidate);
                continue;
            }
            // 남겨둔 대표 야경 명소는 밤에 따로 가는 곳이라, 거리만 가까운 낮 명소(歌舞伎町 아치 옆 新宿東口の猫)는 지우지 않는다.
            // 이름이 겹치는 같은 장소(お台場海浜公園 + お台場海浜公園の展望デッキ)는 지운다 (v11: 낮·밤에 같은 공원)
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

    /** 공백과 괄호 안 내용, 공원 이름 앞의 恩賜·都立 같은 말을 지워서 비교 ("さくら坂 (渋谷)" → "さくら坂", "上野恩賜公園" → "上野公園") */
    static String normalize(String name) {
        return name.replaceAll("\\(.*?\\)|（.*?）", "").replaceAll("\\s", "")
                .replaceAll("恩賜|都立|道立|府立|県立|市立|区立|国営", "");
    }
}
