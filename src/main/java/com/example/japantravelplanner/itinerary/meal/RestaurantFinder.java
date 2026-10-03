package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 한 지점 근처에서 끼니에 맞는 식당 후보를 찾는다.
 * 반경을 넓혀가며 → 그래도 없으면 예산을 올려가며
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestaurantFinder {

    private final RestaurantRepository restaurantRepository;
    private final MealProperties props;

    public List<RestaurantCandidate> find(double lat, double lng, Meal meal, int maxYen,
                                          FoodFilter filter, Set<Long> excludeIds, int limit) {
        // ① 예산 안에서 반경을 넓혀가며
        for (int radius : props.getSearchRadii()) {
            List<RestaurantCandidate> found = search(lat, lng, radius, meal, maxYen, filter, excludeIds, false);
            if (!found.isEmpty()) {
                log.info("[Meal] {} 반경 {}m, 상한 {}엔 → {}곳", meal, radius, maxYen, found.size());
                return found.stream().limit(limit).toList();
            }
        }

        // ② 그래도 없으면 가장 넓은 반경에서 예산을 올려서
        int widest = props.getSearchRadii().get(props.getSearchRadii().size() - 1);
        for (double step : props.getBudgetRelaxSteps()) {
            int relaxed = (int) (maxYen * step);
            List<RestaurantCandidate> found = search(lat, lng, widest, meal, relaxed, filter, excludeIds, true);
            if (!found.isEmpty()) {
                log.info("[Meal] {} 예산 상향 {}엔 → {}곳", meal, relaxed, found.size());
                return found.stream().limit(limit).toList();
            }
        }

        log.warn("[Meal] {} 후보 없음 (위치 {}, {})", meal, lat, lng);
        return List.of();
    }

    private List<RestaurantCandidate> search(double lat, double lng, int radius, Meal meal, int maxYen,
                                             FoodFilter filter, Set<Long> excludeIds, boolean relaxed) {
        List<Restaurant> nearby = restaurantRepository.findWithinRadius(lat, lng, radius);
        double mean = nearby.stream()
                .filter(r -> r.getRatingVal() != null)
                .mapToDouble(Restaurant::getRatingVal)
                .average()
                .orElse(3.5);

        List<RestaurantCandidate> result = new ArrayList<>();
        for (Restaurant r : nearby) {
            if (excludeIds.contains(r.getId()) || r.getRatingVal() == null || r.getLocation() == null) continue;

            Integer budgetMax = meal == Meal.LUNCH ? r.getBudgetLunchMax() : r.getBudgetDinnerMax();
            if (budgetMax == null || budgetMax > maxYen) continue;   // 예산 정보 없음(점심 "-" 등)도 제외

            Set<String> genres = genresOf(r.getGenre());
            if (isSnackOnly(genres)) continue;
            if (!Collections.disjoint(genres, filter.avoidGenres())) continue;
            if (filter.avoidSmoky() && genres.stream().anyMatch(RestaurantFinder::isSmokyGenre)) continue;

            int distance = (int) GeoUtils.distanceMeters(lat, lng,
                    r.getLocation().getY(), r.getLocation().getX());
            int reviews = r.getReviewCnt() == null ? 0 : r.getReviewCnt();
            double score = bayesian(r.getRatingVal(), reviews, mean, props.getPriorReviews())
                    + (Collections.disjoint(genres, filter.preferGenres()) ? 0 : props.getPreferBonus())
                    - distance / 1000.0 * props.getDistancePenaltyPerKm();

            result.add(new RestaurantCandidate(r, distance, score, relaxed));
        }
        result.sort(Comparator.comparingDouble(RestaurantCandidate::score).reversed());
        return result;
    }

    static double bayesian(double rating, int reviews, double mean, int priorReviews) {
        return (reviews * rating + priorReviews * mean) / (reviews + priorReviews);
    }

    static Set<String> genresOf(String genreCsv) {
        if (genreCsv == null || genreCsv.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String g : genreCsv.split(",")) {
            if (!g.isBlank()) result.add(g.trim());
        }
        return result;
    }

    /** 흡연 가능성이 있는 장르: ~バー(바), 居酒屋, 立ち飲み. ハンバーガー·ジュースバー는 해당 없음 */
    static boolean isSmokyGenre(String genre) {
        if (genre.contains("ジュース")) return false;
        return genre.endsWith("バー") || genre.contains("居酒屋") || genre.contains("立ち飲み");
    }

    // 식사가 아니라 간식·디저트·음료가 중심인 장르 (끼니 후보에서 제외)
    private static final List<String> SNACK_KEYWORDS = List.of(
            "菓子", "スイーツ", "ケーキ", "パン", "カフェ", "喫茶", "かき氷", "パフェ", "甘味",
            "フルーツパーラー", "チョコレート", "アイス", "ジェラート", "どら焼き", "大福", "たい焼き",
            "クレープ", "ドーナツ", "マカロン", "プリン", "紅茶", "ジュース", "コーヒー", "団子", "せんべい",
            "ベーグル", "ベーカリー");

    static boolean isSnackGenre(String genre) {
        return SNACK_KEYWORDS.stream().anyMatch(genre::contains);
    }

    /** 장르가 모두 간식·디저트면 식사 후보가 아니다 (カフェ,パスタ처럼 식사가 섞이면 후보) */
    static boolean isSnackOnly(Set<String> genres) {
        return !genres.isEmpty() && genres.stream().allMatch(RestaurantFinder::isSnackGenre);
    }
}