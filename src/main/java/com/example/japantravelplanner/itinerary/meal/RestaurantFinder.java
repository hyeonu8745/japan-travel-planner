package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

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

    /** 방문 날짜를 따지지 않고 찾는다 (정기 휴일 식당도 포함) */
    public List<RestaurantCandidate> find(double lat, double lng, Meal meal, int maxYen,
                                          FoodFilter filter, Set<Long> excludeIds, int limit) {
        return find(lat, lng, meal, maxYen, filter, excludeIds, limit, null);
    }

    /**
     * @param visitDate 방문 날짜. 있으면 그 날이 정기 휴일인 식당은 후보에서 뺀다 (문구가 애매하면 빼지 않는다)
     */
    public List<RestaurantCandidate> find(double lat, double lng, Meal meal, int maxYen,
                                          FoodFilter filter, Set<Long> excludeIds, int limit, LocalDate visitDate) {
        // ① 예산 안에서 반경을 넓혀가며. 후보가 minCandidates곳 이상이 되면 멈춘다
        //    넓은 반경의 결과에는 가까운 곳이 이미 들어 있고, 거리 감점이 있어서 가까운 곳이 계속 유리하다
        List<RestaurantCandidate> nearest = List.of();
        int nearestRadius = 0;
        for (int radius : props.getSearchRadii()) {
            List<RestaurantCandidate> found = search(lat, lng, radius, meal, maxYen, filter, excludeIds, visitDate, false);
            if (found.isEmpty()) continue;

            nearest = found;
            nearestRadius = radius;
            if (found.size() >= props.getMinCandidates()) break;
        }
        if (!nearest.isEmpty()) {
            log.info("[Meal] {} 반경 {}m, 상한 {}엔 → {}곳", meal, nearestRadius, maxYen, nearest.size());
            return nearest.stream().limit(limit).toList();
        }

        // ② 그래도 없으면 가장 넓은 반경에서 예산을 올려서
        int widest = props.getSearchRadii().get(props.getSearchRadii().size() - 1);
        for (double step : props.getBudgetRelaxSteps()) {
            int relaxed = (int) (maxYen * step);
            List<RestaurantCandidate> found = search(lat, lng, widest, meal, relaxed, filter, excludeIds, visitDate, true);
            if (!found.isEmpty()) {
                log.info("[Meal] {} 예산 상향 {}엔 → {}곳", meal, relaxed, found.size());
                return found.stream().limit(limit).toList();
            }
        }

        log.warn("[Meal] {} 후보 없음 (위치 {}, {})", meal, lat, lng);
        return List.of();
    }

    /**
     * 원하는 장르만 찾는다 (채팅 수정 "2일차 저녁을 스시로", 10-11).
     * 보통 검색은 가까운 곳이 3곳 이상이면 500m에서 멈추고 예산을 넘는 곳은 빼서, 스시처럼 비싸거나 드문 장르는
     * 1km 안에 있어도 후보에 들어오지 않았다. 그래서 예산 안에서 가장 넓은 반경까지 → 그래도 없으면 예산을 올려서 찾는다.
     * 다른 조건(피할 음식, 흡연, 폐업 의심, 간식·술 위주 제외)은 보통 검색과 같다.
     */
    public List<RestaurantCandidate> findWanted(double lat, double lng, Meal meal, int maxYen, FoodFilter filter,
                                                Set<Long> excludeIds, int limit, Set<String> wanted) {
        if (wanted == null || wanted.isEmpty()) return List.of();
        // ① 보통 반경(1.5km까지) × 예산 단계 → ② 그래도 없으면 더 넓은 반경(2·3km) × 예산 단계 (10-11 여행 9:
        //    "5일차 점심 장어덮밥" → 진구마에 근처 1.5km 안에 장어집이 없어 카레집만 나왔다)
        List<RestaurantCandidate> found = findWanted(lat, lng, meal, maxYen, filter, excludeIds, limit, wanted, c -> true);
        if (found.isEmpty()) log.info("[Meal] {} 원하는 장르 {} 후보 없음", meal, wanted);
        return found;
    }

    /**
     * 채팅에서 고른 식당이 "원하는 장르 넓게 찾기"의 후보가 될 수 있는 곳인지 (교체 검증용, 10-11).
     * 그 식당의 장르로 다시 찾으면 같은 장르의 더 가까운 곳(일식 등)이 먼저 나와 1.8km 떨어진 고른 곳까지 가지 않았다.
     * 그래서 같은 반경·예산 단계를 그 식당 하나만 놓고 따라간다
     */
    public Optional<RestaurantCandidate> findWantedOne(double lat, double lng, Meal meal, int maxYen, FoodFilter filter,
                                                       Set<Long> excludeIds, Set<String> wanted, Long restaurantId) {
        if (wanted == null || wanted.isEmpty() || restaurantId == null) return Optional.empty();
        return findWanted(lat, lng, meal, maxYen, filter, excludeIds, 1, wanted,
                c -> restaurantId.equals(c.restaurant().getId())).stream().findFirst();
    }

    private List<RestaurantCandidate> findWanted(double lat, double lng, Meal meal, int maxYen, FoodFilter filter,
                                                 Set<Long> excludeIds, int limit, Set<String> wanted,
                                                 java.util.function.Predicate<RestaurantCandidate> only) {
        List<RestaurantCandidate> found = findWanted(lat, lng, meal, maxYen, filter, excludeIds, limit, wanted,
                props.getSearchRadii(), only);
        if (found.isEmpty() && props.getWantedExtraRadii() != null && !props.getWantedExtraRadii().isEmpty()) {
            found = findWanted(lat, lng, meal, maxYen, filter, excludeIds, limit, wanted, props.getWantedExtraRadii(), only);
        }
        return found;
    }

    private List<RestaurantCandidate> findWanted(double lat, double lng, Meal meal, int maxYen, FoodFilter filter,
                                                 Set<Long> excludeIds, int limit, Set<String> wanted,
                                                 List<Integer> radii,
                                                 java.util.function.Predicate<RestaurantCandidate> only) {
        List<Double> factors = new ArrayList<>();
        factors.add(1.0);
        factors.addAll(props.getBudgetRelaxSteps());
        for (double factor : factors) {
            for (int radius : radii) {
                List<RestaurantCandidate> found = search(lat, lng, radius, meal, (int) (maxYen * factor), filter,
                        excludeIds, null, factor > 1.0).stream()
                        .filter(c -> !Collections.disjoint(genresOf(c.restaurant().getGenre()), wanted))
                        .filter(only)
                        .limit(limit).toList();
                if (!found.isEmpty()) {
                    log.info("[Meal] {} 원하는 장르 {} 반경 {}m, 상한 {}엔 → {}곳", meal, wanted, radius,
                            (int) (maxYen * factor), found.size());
                    return found;
                }
            }
        }
        return List.of();
    }

    private List<RestaurantCandidate> search(double lat, double lng, int radius, Meal meal, int maxYen,
                                             FoodFilter filter, Set<Long> excludeIds, LocalDate visitDate,
                                             boolean relaxed) {
        List<Restaurant> nearby = restaurantRepository.findWithinRadius(lat, lng, radius);
        double mean = nearby.stream()
                .filter(r -> r.getRatingVal() != null)
                .mapToDouble(Restaurant::getRatingVal)
                .average()
                .orElse(3.5);

        List<RestaurantCandidate> result = new ArrayList<>();
        for (Restaurant r : nearby) {
            if (excludeIds.contains(r.getId()) || r.getRatingVal() == null || r.getLocation() == null) continue;
            if (r.isExcludedByGoogleMatch()) continue;   // Google에서 찾지 못함·폐업 (폐업했을 가능성이 큼)
            if (RegularHoliday.isClosedOn(r.getHoliday(), visitDate)) continue;   // 방문하는 날이 정기 휴일

            Integer budgetMax = meal == Meal.LUNCH ? r.getBudgetLunchMax() : r.getBudgetDinnerMax();
            if (budgetMax == null || budgetMax > maxYen) continue;   // 예산 정보 없음(점심 "-" 등)도 제외

            Set<String> genres = genresOf(r.getGenre());
            if (isSnackOnly(genres)) continue;
            if (meal == Meal.DINNER && isSnackLed(r.getGenre())) continue;   // 저녁은 간식이 대표인 곳 제외
            if (isDrinkLed(r.getGenre())) continue;   // 술이 중심인 바는 끼니 후보가 아니다
            if (filter.excludes(genres)) continue;   // 피할 음식·알레르기 장르, 담배

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

    public static Set<String> genresOf(String genreCsv) {
        if (genreCsv == null || genreCsv.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String g : genreCsv.split(",")) {
            if (!g.isBlank()) result.add(g.trim());
        }
        return result;
    }

    /**
     * 흡연 가능성이 있는 장르: ~バー(바), バル·バール(스페인식 바), 居酒屋, 立ち飲み.
     * ハンバーガー·ジュースバー·ネパール料理는 해당 없음
     * (バル·バール를 빼먹어서 「スペイン料理,バル・バール」가 "담배 연기 싫음" 여행의 저녁으로 뽑힌 적이 있다)
     */
    static boolean isSmokyGenre(String genre) {
        if (genre.contains("ジュース")) return false;
        return genre.endsWith("バー") || genre.contains("バル") || genre.contains("バール")
                || genre.contains("居酒屋") || genre.contains("立ち飲み");
    }

    // ===================== 쉬는 시간 카페 (10-16) =====================

    /** 앉아서 쉬는 곳으로 볼 대표 장르 (빵집·과자점·떡집처럼 사 가는 곳은 뺀다) */
    private static final List<String> CAFE_KEYWORDS = List.of(
            "カフェ", "喫茶", "スイーツ", "ケーキ", "パフェ", "甘味", "フルーツパーラー", "かき氷", "クレープ",
            "パンケーキ", "紅茶", "コーヒー", "ジェラート", "アイス", "チョコレート");

    /** 대표(첫 번째) 장르가 카페·디저트인지 */
    public static boolean isCafeLed(String genreCsv) {
        String first = firstGenre(genreCsv);
        return !first.isEmpty() && CAFE_KEYWORDS.stream().anyMatch(first::contains);
    }

    /**
     * 오후 쉬는 시간 카페 후보 (10-16). 반경을 넓혀가며 찾고, 평점에서 거리(km)×distancePenalty를 뺀 점수순.
     * 대표 장르가 카페·디저트인 곳, 폐업 의심·그날 정기 휴일 제외, 1인 예산(점심 → 없으면 저녁 상한)이 maxYen 넘는 곳 제외
     * (예산 정보가 없으면 통과 — 원칙 6). 끼니 후보처럼 흡연 장르·술집은 걸러진다(대표 장르가 카페라서).
     * 10-16: 끼니와 같은 음식 조건(피할 음식·알레르기·담배)을 적용한다. 처음에는 빠져 있어 "디저트는 빼 주세요"에도 카페가 들어갔다
     */
    public List<RestaurantCandidate> findCafe(double lat, double lng, FoodFilter filter, Set<Long> excludeIds, LocalDate visitDate,
                                              List<Integer> radii, int maxYen, double distancePenalty, int limit) {
        for (int radius : radii) {
            List<RestaurantCandidate> found = new ArrayList<>();
            for (Restaurant r : restaurantRepository.findWithinRadius(lat, lng, radius)) {
                if (excludeIds.contains(r.getId()) || r.getRatingVal() == null || r.getLocation() == null) continue;
                if (r.isExcludedByGoogleMatch()) continue;
                if (RegularHoliday.isClosedOn(r.getHoliday(), visitDate)) continue;
                if (!isCafeLed(r.getGenre()) || isDrinkLed(r.getGenre())) continue;
                if (filter != null && filter.excludes(genresOf(r.getGenre()))) continue;   // 10-16: 디저트·카페를 피하거나 밀가루 알레르기 등
                Integer budget = r.getBudgetLunchMax() != null ? r.getBudgetLunchMax() : r.getBudgetDinnerMax();
                if (budget != null && budget > maxYen) continue;
                double km = GeoUtils.distanceMeters(lat, lng, r.getLocation().getY(), r.getLocation().getX()) / 1000.0;
                found.add(new RestaurantCandidate(r, (int) Math.round(km * 1000), r.getRatingVal() - km * distancePenalty, false));
            }
            if (!found.isEmpty()) {
                found.sort(Comparator.comparingDouble(RestaurantCandidate::score).reversed());
                log.info("[Cafe] 반경 {}m → {}곳", radius, found.size());
                return found.stream().limit(limit).toList();
            }
        }
        return List.of();
    }

    // 식사가 아니라 간식·디저트·음료가 중심인 장르 (끼니 후보에서 제외)
    private static final List<String> SNACK_KEYWORDS = List.of(
            "菓子", "スイーツ", "ケーキ", "パン", "カフェ", "喫茶", "かき氷", "パフェ", "甘味",
            "フルーツパーラー", "チョコレート", "アイス", "ジェラート", "どら焼き", "大福", "たい焼き",
            "クレープ", "ドーナツ", "マカロン", "プリン", "紅茶", "ジュース", "コーヒー", "団子", "せんべい",
            "ベーグル", "ベーカリー", "ラウンジ");

    static boolean isSnackGenre(String genre) {
        return SNACK_KEYWORDS.stream().anyMatch(genre::contains);
    }

    /** 타베로그는 첫 번째 장르가 대표 장르 */
    static String firstGenre(String genreCsv) {
        if (genreCsv == null || genreCsv.isBlank()) return "";
        return genreCsv.split(",")[0].trim();
    }

    /**
     * 대표(첫 번째) 장르가 간식·디저트·카페면 저녁 후보가 아니다.
     * 모든 장르가 간식일 때만 거르는 isSnackOnly로는 カフェ,洋食,パン 같은 곳이 통과한다
     */
    static boolean isSnackLed(String genreCsv) {
        String first = firstGenre(genreCsv);
        return !first.isEmpty() && isSnackGenre(first);
    }

    /**
     * 술이 중심인 곳: 대표(첫 번째) 장르가 바이고, 두 번째 장르도 식사가 아닌 곳 (없거나, 바·간식·라운지).
     * 「バー,ラウンジ,ワインバー」(호텔 라운지 바), 「バー」만 있는 곳이 여행 저녁으로 뽑힌 적이 있다.
     * 「バー,ビストロ」「ダイニングバー,イタリアン」처럼 두 번째가 식사면 식사하는 곳으로 보고 남긴다.
     */
    static boolean isDrinkLed(String genreCsv) {
        List<String> main = List.copyOf(mainGenres(genreCsv));
        if (main.isEmpty() || !isBarGenre(main.get(0))) return false;
        if (main.size() == 1) return true;
        String second = main.get(1);
        return isBarGenre(second) || isSnackGenre(second);
    }

    /** 바 종류 장르 (バー, ワインバー, ダイニングバー, バー・お酒（その他）, パブ, ビアホール 등). ハンバーガー·バーベキュー·ジュースバー는 아니다 */
    static boolean isBarGenre(String genre) {
        if (genre.contains("ジュース")) return false;
        return genre.endsWith("バー") || genre.startsWith("バー・") || genre.contains("パブ")
                || genre.contains("ビアホール") || genre.contains("ビアガーデン");
    }

    /** 장르가 모두 간식·디저트면 식사 후보가 아니다 (カフェ,パスタ처럼 식사가 섞이면 후보) */
    static boolean isSnackOnly(Set<String> genres) {
        return !genres.isEmpty() && genres.stream().allMatch(RestaurantFinder::isSnackGenre);
    }

    /**
     * 대표 장르: 앞의 두 개. 타베로그는 첫 번째 장르가 대표라서, 뒤쪽 장르가 겹친다고 같은 음식으로 보지 않는다.
     * (そば,うどん,カレーライス는 세 번째 장르 때문에 카레 점심과 같은 음식으로 잘못 걸렸다)
     */
    public static Set<String> mainGenres(String genreCsv) {
        if (genreCsv == null || genreCsv.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String g : genreCsv.split(",")) {
            if (!g.isBlank()) result.add(g.trim());
            if (result.size() == 2) break;
        }
        return result;
    }
}