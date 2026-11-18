package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;

import java.util.Arrays;
import java.util.Map;

/**
 * 저녁 뒤 야경 장소.
 *
 * 그날 지역에서 점수가 가장 높은 전망대를 무조건 넣으면 저녁이 전망대만 반복되고 대표성이 낮은 곳도 들어간다.
 * 또 전망대가 없는 지역(오다이바)은 야경이 비고, 그 지역 야경의 대표(레인보우브리지 쪽)를 낮에 들르게 된다.
 *
 * 그래서 저녁 뒤에는 아래 대표 야경 명소만 넣고, 종류(전망대·물가·거리·야간 조명)를 섞는다 (NightSpotPlanner).
 * 이름은 관광지 데이터의 name 그대로다. 데이터를 다시 모으면 이름이 맞는지 확인해야 한다 (11-18 확장 데이터 이름 추가).
 * 확장 데이터에서 야경 명소로 표시된 곳(nightView)도 대표 명소로 본다.
 */
public final class NightSpots {

    public enum Kind {
        OBSERVATORY,   // 전망대
        WATERFRONT,    // 바다·다리가 보이는 물가
        CITY_LIGHTS,   // 네온·교차로 같은 거리 야경
        LIGHT_UP       // 밤에 조명을 켜는 명소
    }

    static final String OBSERVATION_DECK = "observation_deck";

    /**
     * 대표 야경 명소 → 종류 (도쿄, 직접 고른 목록). 11-18 확장 데이터로 이름이 바뀐 곳은 새 이름도 같이 둔다
     * (SHIBUYA SKY → Shibuya Sky, 浅草寺 → 金龍山 浅草寺, 東京シティビュー, 東京都庁展望室 北, 歌舞伎町一番街通り).
     * 다른 도시는 확장 데이터의 야경 명소 표시(nightView)와 야경 종류(nightKind)를 쓴다
     */
    static final Map<String, Kind> REPRESENTATIVE = Map.ofEntries(
            Map.entry("東京タワー", Kind.OBSERVATORY),
            Map.entry("SHIBUYA SKY", Kind.OBSERVATORY),
            Map.entry("Shibuya Sky", Kind.OBSERVATORY),
            Map.entry("六本木ヒルズ展望台 東京シティビュー", Kind.OBSERVATORY),
            Map.entry("東京シティビュー", Kind.OBSERVATORY),
            Map.entry("東京都庁第一本庁舎 北展望室", Kind.OBSERVATORY),
            Map.entry("東京都庁展望室 北", Kind.OBSERVATORY),
            Map.entry("お台場海浜公園", Kind.WATERFRONT),          // 레인보우브리지 야경을 보는 해변 (다리 산책로는 밤에 닫는다)
            Map.entry("渋谷スクランブル交差点", Kind.CITY_LIGHTS),
            Map.entry("歌舞伎町一番街アーチ", Kind.CITY_LIGHTS),
            Map.entry("歌舞伎町一番街通り", Kind.CITY_LIGHTS),
            Map.entry("浅草寺", Kind.LIGHT_UP),
            Map.entry("金龍山 浅草寺", Kind.LIGHT_UP));

    /** 확장 데이터의 야경 종류 글자 → 종류 */
    private static final Map<String, Kind> DATA_KIND = Map.of(
            "전망대", Kind.OBSERVATORY,
            "거리 야경", Kind.CITY_LIGHTS,
            "일루미네이션", Kind.LIGHT_UP,
            "야간 개장", Kind.LIGHT_UP);

    /** 정적 메서드만 쓴다 */
    private NightSpots() {
    }

    /** 일정을 만들 때 저녁 뒤에 넣는 대표 야경 명소인지 */
    public static boolean isRepresentative(Attraction attraction) {
        return attraction != null && (REPRESENTATIVE.containsKey(attraction.getName()) || attraction.isNightView());
    }

    /** 야경 종류. 대표 명소가 아니면 전망대만 OBSERVATORY, 나머지는 null */
    public static Kind kindOf(Attraction attraction) {
        if (attraction == null) return null;
        Kind kind = REPRESENTATIVE.get(attraction.getName());
        if (kind != null) return kind;
        if (attraction.isNightView()) return dataKind(attraction);
        return isObservatory(attraction) ? Kind.OBSERVATORY : null;
    }

    /** 사용자가 야경 자리를 직접 바꿀 때의 후보 (대표 명소 + 모든 전망대) */
    public static boolean isNightCandidate(Attraction attraction) {
        return kindOf(attraction) != null;
    }

    /** 확장 데이터 야경 명소의 종류 (종류 글자가 없거나 모르는 값이면 전망대면 OBSERVATORY, 아니면 LIGHT_UP) */
    private static Kind dataKind(Attraction attraction) {
        String text = attraction.getNightInfo() == null ? null : attraction.getNightInfo().getKind();
        Kind kind = text == null ? null : DATA_KIND.get(text.trim());
        if (kind != null) return kind;
        return isObservatory(attraction) ? Kind.OBSERVATORY : Kind.LIGHT_UP;
    }

    /** 전망대(observation_deck)인지 */
    public static boolean isObservatory(Attraction attraction) {
        if (attraction == null || attraction.typesOrKind() == null) return false;
        return Arrays.stream(attraction.typesOrKind().split(",")).map(String::trim).anyMatch(OBSERVATION_DECK::equals);
    }
}
