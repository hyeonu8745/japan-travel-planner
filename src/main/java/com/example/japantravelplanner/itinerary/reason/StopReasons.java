package com.example.japantravelplanner.itinerary.reason;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.InterestTypes;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.meal.RestaurantFinder;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Interest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일정 항목마다 "왜 이곳인지" 짧은 이유 (화면의 칩). AI가 아니라 서버가 고를 때 쓴 기준에서만 만든다.
 *  - 관광지: 관심사와 맞는 종류, 높은 Google 평점, 아주 많은 리뷰 (AttractionScorer 점수 = 보정 평점 + 관심사 보너스)
 *  - 식당: 좋아하는 음식 장르, 높은 타베로그 평점 (RestaurantFinder 점수 = 보정 평점 + 선호 장르 보너스 - 거리 감점)
 *  - 공통: 앞 장소(첫 항목은 숙소)에서 걸어서 가까움
 * 저장하지 않고 응답을 만들 때마다 계산한다. 교체·추가·되돌리기 뒤에도 그 항목에 맞는 이유가 붙는다.
 * 중요한 것부터 최대 MAX_REASONS개. 이유가 없으면 빈 목록이다.
 */
public final class StopReasons {

    static final int MAX_REASONS = 3;
    /**
     * Google 평점이 이 이상이면 "평점 높음". 도쿄 관광지는 4.5가 흔해서(10-10 실제 일정에서 21곳 중 13곳)
     * 4.5로는 칩이 거의 모든 곳에 붙어 의미가 없었다. 4.6이면 21곳 중 4곳
     */
    static final double HIGH_GOOGLE_RATING = 4.6;
    /** Google 리뷰가 이 이상이면 "많이 찾는 곳" */
    static final int MANY_REVIEWS = 10_000;
    /** 타베로그 평점이 이 이상이면 "평점 높음" (3.5 이상이면 좋은 편, 3.6 이상은 드물다) */
    static final double HIGH_TABELOG_RATING = 3.6;
    static final int NEAR_WALK_MINUTES = 10;

    private StopReasons() {
    }

    public static List<StopReason> of(ItineraryStop stop, ReasonContext context) {
        ReasonContext ctx = ReasonContext.orNone(context);
        List<StopReason> reasons = new ArrayList<>();
        if (stop.getType() == StopType.ATTRACTION) {
            addAttractionReasons(reasons, stop.getAttraction(), ctx);
        } else {
            addRestaurantReasons(reasons, stop.getRestaurant(), ctx);
        }
        StopReason near = near(stop);
        if (near != null) reasons.add(near);
        return reasons.stream().limit(MAX_REASONS).toList();
    }

    /** 관광지 종류와 맞는 관심사 (여행 조건에 고른 순서대로) */
    public static List<Interest> matchedInterests(Attraction attraction, ReasonContext context) {
        if (attraction == null || attraction.getTypes() == null) return List.of();
        Set<String> types = Arrays.stream(attraction.getTypes().split(","))
                .map(String::trim).collect(Collectors.toSet());
        return ReasonContext.orNone(context).interests().stream()
                .filter(interest -> InterestTypes.of(interest).stream().anyMatch(types::contains))
                .toList();
    }

    /** 대화에서 말한 좋아하는 음식 장르가 있는 식당인지 (식당을 고를 때와 같은 기준: 장르 중 하나라도) */
    public static boolean matchesPreferredFood(Restaurant restaurant, ReasonContext context) {
        if (restaurant == null) return false;
        Set<String> prefer = ReasonContext.orNone(context).food().preferGenres();
        return !prefer.isEmpty() && !Collections.disjoint(RestaurantFinder.genresOf(restaurant.getGenre()), prefer);
    }

    public static String interestLabel(Interest interest) {
        return switch (interest) {
            case FOOD -> "현지 음식";
            case TRADITIONAL -> "전통 명소";
            case SHOPPING -> "쇼핑";
            case NIGHT_VIEW -> "야경";
        };
    }

    private static void addAttractionReasons(List<StopReason> reasons, Attraction a, ReasonContext ctx) {
        if (a == null) return;
        for (Interest interest : matchedInterests(a, ctx)) {
            reasons.add(new StopReason(ReasonCode.INTEREST, "관심사: " + interestLabel(interest)));
        }
        if (a.getRating() != null && a.getRating() >= HIGH_GOOGLE_RATING) {
            reasons.add(new StopReason(ReasonCode.HIGH_RATING,
                    "Google 평점 " + String.format(Locale.ROOT, "%.1f", a.getRating())));
        }
        if (a.getReviewCnt() != null && a.getReviewCnt() >= MANY_REVIEWS) {
            reasons.add(new StopReason(ReasonCode.MANY_REVIEWS, "리뷰 " + tenThousands(a.getReviewCnt()) + "개"));
        }
    }

    private static void addRestaurantReasons(List<StopReason> reasons, Restaurant r, ReasonContext ctx) {
        if (r == null) return;
        if (matchesPreferredFood(r, ctx)) {
            reasons.add(new StopReason(ReasonCode.FOOD_PREFER, "좋아하는 음식"));
        }
        if (r.getRatingVal() != null && r.getRatingVal() >= HIGH_TABELOG_RATING) {
            reasons.add(new StopReason(ReasonCode.HIGH_RATING,
                    "타베로그 " + String.format(Locale.ROOT, "%.2f", r.getRatingVal())));
        }
    }

    // 첫 항목의 이동은 숙소에서 출발한 것
    private static StopReason near(ItineraryStop stop) {
        if (stop.getTravelMode() != LegMode.WALK || stop.getTravelMinutes() > NEAR_WALK_MINUTES) return null;
        int minutes = Math.max(1, stop.getTravelMinutes());
        String from = stop.getStopOrder() == 1 ? "숙소에서" : "앞 장소에서";
        return new StopReason(ReasonCode.NEAR, from + " 걸어서 " + minutes + "분");
    }

    /** 12345 → "1.2만", 80000 → "8만" */
    static String tenThousands(int count) {
        String value = String.format(Locale.ROOT, "%.1f", Math.floor(count / 1000.0) / 10.0);
        return (value.endsWith(".0") ? value.substring(0, value.length() - 2) : value) + "만";
    }
}
