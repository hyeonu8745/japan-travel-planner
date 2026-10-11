package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.trip.entity.Interest;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관광지가 관심사와 맞는지 (점수 가산 AttractionScorer, 고른 이유 칩 StopReasons가 같이 쓴다)
 *  - 야경: 대표 야경 명소(NightSpots)만. 예전에는 observation_deck·landmark·bridge type이면 모두 맞다고 봐서
 *    SKY VIEW(무료 전망 공간)가 가산점을 받아 아침 첫 장소로 들어가고 "관심사: 야경" 칩까지 붙었다 (10-10 여행 8 v9)
 *  - 나머지: Google Places type
 */
public final class InterestTypes {

    private InterestTypes() {
    }

    private static final Map<Interest, Set<String>> TYPES = Map.of(
            // 박물관·미술관은 뺐다: 데이터를 넓히니 팀랩·건담 베이스·세이코 뮤지엄까지 "관심사: 전통 명소"가 됐다 (10-10 v10)
            Interest.TRADITIONAL, Set.of("buddhist_temple", "shinto_shrine", "place_of_worship",
                    "historical_landmark", "historical_place"),
            // store, clothing_store 같은 범용 태그는 모스크·박물관 기념품점에도 붙어서 제외
            Interest.SHOPPING, Set.of("shopping_mall", "market", "department_store"),
            // food_store는 식료품점 태그라 관광지 성격과 무관해서 제외
            Interest.FOOD, Set.of("market")
    );

    /** 이름에 이 말이 있으면 market이어도 먹거리 시장이 아니다 (주방용품 거리) */
    private static final java.util.List<String> NOT_FOOD_NAMES = java.util.List.of("道具", "かっぱ橋", "合羽橋");

    /** 관심사와 어울리는 type (야경은 type이 아니라 NightSpots 목록으로 판단해서 비어 있다) */
    public static Set<String> of(Interest interest) {
        return TYPES.getOrDefault(interest, Set.of());
    }

    public static boolean matches(Interest interest, Attraction attraction) {
        if (attraction == null) return false;
        if (interest == Interest.NIGHT_VIEW) return NightSpots.isRepresentative(attraction);
        if (attraction.getTypes() == null) return false;
        Set<String> types = Arrays.stream(attraction.getTypes().split(","))
                .map(String::trim).collect(Collectors.toSet());
        if (interest == Interest.FOOD && !isFoodMarket(attraction, types)) return false;
        return of(interest).stream().anyMatch(types::contains);
    }

    /**
     * market type 중 먹거리와 상관없는 곳은 '현지 음식'으로 보지 않는다.
     * 10-10 요약에 "관심사 '현지 음식'에 맞는 곳: アメ横商店街, かっぱ橋道具街"가 나왔다 — 갓파바시는 주방용품 거리다.
     * 할인점(ドン・キホーテ)도 market이 붙어 있어 뺀다
     */
    private static boolean isFoodMarket(Attraction attraction, Set<String> types) {
        String name = attraction.getName() == null ? "" : attraction.getName();
        if (NOT_FOOD_NAMES.stream().anyMatch(name::contains)) return false;
        return !types.contains("discount_store");
    }
}
