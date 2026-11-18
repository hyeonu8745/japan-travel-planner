package com.example.japantravelplanner.attraction.entity;

import java.util.regex.Pattern;

/**
 * 관광지 종류 (확장 데이터의 OSM 태그를 정리한 값). 도쿄 예전 데이터는 Google types 를 쓰고 이 칸이 비어 있다.
 * 체류 시간·종류 섞기·관심사·실내외 판단은 Google type 이름으로 되어 있어서, {@link #toTypes(String)} 로 같은 이름표를 만든다.
 */
public enum AttractionKind {
    TEMPLE("buddhist_temple,place_of_worship,tourist_attraction"),
    SHRINE("shinto_shrine,place_of_worship,tourist_attraction"),
    MUSEUM("museum,tourist_attraction"),
    PARK("park,tourist_attraction"),
    // 전망대와 그냥 경치 보는 곳이 섞여 있다 → 이름으로 나눈다 (toTypes)
    VIEW("scenic_spot,tourist_attraction"),
    HISTORIC("historical_landmark,tourist_attraction"),
    // 성·궁궐은 넓은 터를 걷는 곳이라 공원 체류 시간(75분)을 쓴다
    CASTLE("historical_landmark,park,tourist_attraction"),
    SHOPPING("shopping_mall,tourist_attraction"),
    THEME("amusement_park,tourist_attraction"),
    ATTRACTION("tourist_attraction"),
    WORSHIP("place_of_worship,tourist_attraction");

    /**
     * 이름에 이 말이 있는 VIEW·ATTRACTION 은 건물 전망대 (Shibuya Sky, 東京シティビュー, 東京都庁展望室, 東京タワー).
     * 산·공원의 「展望台」「展望所」·view point 는 밖에서 경치를 보는 곳이라 넣지 않는다 (비 오는 날 실내로 보면 안 된다)
     */
    private static final Pattern OBSERVATORY_NAME = Pattern.compile(
            "展望室|展望ロビー|展望スペース|展望フロア|展望デッキ|シティビュー|タワー|スカイ|(?i:sky|tower|observatory|observation)");

    private static final String OBSERVATORY_TYPES = "observation_deck,tourist_attraction";

    private final String types;

    AttractionKind(String types) {
        this.types = types;
    }

    /**
     * Google type 과 같은 이름으로 쓴 종류 (콤마 구분).
     * VIEW·ATTRACTION 은 이름에 전망대 말이 있으면 observation_deck (東京タワー·東京スカイツリー 가 ATTRACTION 으로 들어 있다)
     */
    public String toTypes(String name) {
        if ((this == VIEW || this == ATTRACTION) && name != null && OBSERVATORY_NAME.matcher(name).find()) {
            return OBSERVATORY_TYPES;
        }
        return types;
    }
}
