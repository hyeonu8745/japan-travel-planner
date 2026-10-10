package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;

import java.util.Arrays;
import java.util.Map;

/**
 * 저녁 뒤 야경 장소.
 *
 * 예전에는 그날 지역에서 점수가 가장 높은 전망대(observation_deck)를 무조건 넣었다.
 * 10-10 여행 8 v8에서 6일 중 4일 저녁이 전망대였고, SKY VIEW(카렛타 시오도메 무료 전망 공간, 리뷰 513)처럼
 * 대표성이 낮은 곳도 들어갔다. 반대로 오다이바 날은 전망대가 없어서 야경이 비고,
 * 오다이바 야경의 대표인 레인보우브리지 쪽은 낮에 들렀다.
 *
 * 그래서 저녁 뒤에는 아래 대표 야경 명소만 넣고, 종류(전망대·물가·거리·야간 조명)를 섞는다 (NightSpotPlanner).
 * 이름은 관광지 데이터(2026-10-10, 119곳)의 name 그대로다. 데이터를 다시 모으면 이름이 맞는지 확인해야 한다.
 */
public final class NightSpots {

    public enum Kind {
        OBSERVATORY,   // 전망대
        WATERFRONT,    // 바다·다리가 보이는 물가
        CITY_LIGHTS,   // 네온·교차로 같은 거리 야경
        LIGHT_UP       // 밤에 조명을 켜는 명소
    }

    static final String OBSERVATION_DECK = "observation_deck";

    /** 대표 야경 명소 → 종류 */
    static final Map<String, Kind> REPRESENTATIVE = Map.of(
            "東京タワー", Kind.OBSERVATORY,
            "SHIBUYA SKY", Kind.OBSERVATORY,
            "六本木ヒルズ展望台 東京シティビュー", Kind.OBSERVATORY,
            "東京都庁第一本庁舎 北展望室", Kind.OBSERVATORY,
            "お台場海浜公園", Kind.WATERFRONT,          // 레인보우브리지 야경을 보는 해변 (다리 산책로는 밤에 닫는다)
            "渋谷スクランブル交差点", Kind.CITY_LIGHTS,
            "歌舞伎町一番街アーチ", Kind.CITY_LIGHTS,
            "浅草寺", Kind.LIGHT_UP);

    private NightSpots() {
    }

    /** 일정을 만들 때 저녁 뒤에 넣는 대표 야경 명소인지 */
    public static boolean isRepresentative(Attraction attraction) {
        return attraction != null && REPRESENTATIVE.containsKey(attraction.getName());
    }

    /** 야경 종류. 대표 명소가 아니면 전망대만 OBSERVATORY, 나머지는 null */
    public static Kind kindOf(Attraction attraction) {
        if (attraction == null) return null;
        Kind kind = REPRESENTATIVE.get(attraction.getName());
        if (kind != null) return kind;
        return isObservatory(attraction) ? Kind.OBSERVATORY : null;
    }

    /** 사용자가 야경 자리를 직접 바꿀 때의 후보 (대표 명소 + 모든 전망대) */
    public static boolean isNightCandidate(Attraction attraction) {
        return kindOf(attraction) != null;
    }

    public static boolean isObservatory(Attraction attraction) {
        if (attraction == null || attraction.getTypes() == null) return false;
        return Arrays.stream(attraction.getTypes().split(",")).map(String::trim).anyMatch(OBSERVATION_DECK::equals);
    }
}
