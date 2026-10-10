package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.entity.Trip;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 여행 하나의 이동 방식. 동선·끼니·시간 계산이 모두 같은 값을 쓰도록 한 곳에서 정한다.
 *
 * 걷는 기준 (10-10 사용자 결정: 기본은 30분까지 걷는다, 가까운 곳을 원하면 예전처럼 좁게)
 *  1. 여유 속도이거나 조건 입력에 이동 제약이 있으면 짧게 (relaxed, 가까운 곳 위주)
 *  2. 대화의 이동 조건에 "N분 정도 걸어서"처럼 시간이 있으면 그 시간 (10~40분, 기본보다 짧으면 가까운 곳 위주)
 *  3. 대화에서 가까운 곳·적게 걷기·계단·체력 이야기가 있으면 짧게 (가까운 곳 위주)
 *  4. 그 밖에는 기본 (30분, 지역 안에서 넓게)
 * 예전에는 대화에 이동 조건이 하나라도 있으면 무조건 짧게 해서, "30분 정도 거리면 걸어서 다니고 싶어"(여행 8)가
 * 오히려 도보 10분으로 처리됐다.
 *
 * @param mode                 기본 이동수단 (대중교통 또는 렌트카)
 * @param walkThresholdMinutes 이 시간 이내는 걸어감
 * @param compact              가까운 곳 위주 (true면 하루 관광지를 점수순으로만 고르고, false면 지역 안에서 종류·위치를 섞는다)
 */
public record TravelOptions(LegMode mode, int walkThresholdMinutes, boolean compact) {

    static final int MIN_WALK_MINUTES = 10;
    static final int MAX_WALK_MINUTES = 40;

    /**
     * 가까운 곳 위주로 다니고 싶거나 많이 걷기 어렵다는 말 (대화의 이동 조건).
     * 맨 "가까운"은 넣지 않는다: "가까운 곳은 걸어서"는 오히려 걷겠다는 말이다
     */
    static final List<String> GENTLE_WORDS = List.of(
            "가까운 곳 위주", "가까운 곳만", "가까운 데 위주", "가까운 데만", "가까운 곳으로만", "가까이만", "근처만",
            "멀리 가기 싫", "멀리 안", "이동 적게", "이동을 적게", "이동은 적게", "동선 짧게", "동선이 짧",
            "적게 걷", "덜 걷", "조금만 걷", "걷기 싫", "걷는 거 싫", "걷는건 싫", "많이 못 걷", "오래 못 걷", "많이 걷기 힘",
            "계단", "체력", "무릎", "허리", "다리가 아", "유모차", "휠체어", "어르신", "노약");

    /** "30분 정도 걸어서", "도보 20분" 같은 말에서 분을 찾는다 */
    private static final Pattern WALK_MINUTES = Pattern.compile("(\\d{1,2})\\s*분[^.,]{0,12}?(걸|걷|도보)|(걸|걷|도보)[^.,]{0,8}?(\\d{1,2})\\s*분");

    /** 테스트·기존 코드용 (가까운 곳 위주가 아님) */
    public TravelOptions(LegMode mode, int walkThresholdMinutes) {
        this(mode, walkThresholdMinutes, false);
    }

    public static TravelOptions of(Trip trip, List<PreferenceConstraint> constraints, TravelProperties props) {
        LegMode mode = trip.getTransportMode() == TransportMode.RENTAL_CAR ? LegMode.DRIVE : LegMode.TRANSIT;

        List<String> mobility = constraints == null ? List.of() : constraints.stream()
                .filter(c -> c.getCategory() == ConstraintCategory.MOBILITY)
                .map(PreferenceConstraint::getDescription)
                .filter(d -> d != null && !d.isBlank())
                .toList();

        // 조건 입력(여유 속도, 이동 제약)은 사용자가 직접 고른 것이라 그대로 짧게
        boolean conditionGentle = trip.getTravelPace() == TravelPace.RELAXED
                || (trip.getMobilityConstraints() != null && !trip.getMobilityConstraints().isBlank());
        boolean chatGentle = mobility.stream().anyMatch(TravelOptions::wantsGentle);
        Integer stated = mobility.stream().map(TravelOptions::statedWalkMinutes)
                .filter(m -> m != null).findFirst().orElse(null);

        if (conditionGentle) {
            return new TravelOptions(mode, props.getRelaxedWalkThresholdMinutes(), true);
        }
        if (stated != null) {
            // "가까운 곳은 30분 정도 걸어서"처럼 가깝다는 말과 시간이 같이 오면 시간을 따른다
            int threshold = Math.max(MIN_WALK_MINUTES, Math.min(MAX_WALK_MINUTES, stated));
            return new TravelOptions(mode, threshold, threshold < props.getWalkThresholdMinutes());
        }
        return chatGentle
                ? new TravelOptions(mode, props.getRelaxedWalkThresholdMinutes(), true)
                : new TravelOptions(mode, props.getWalkThresholdMinutes(), false);
    }

    static boolean wantsGentle(String text) {
        if (text == null) return false;
        return GENTLE_WORDS.stream().anyMatch(text::contains);
    }

    /** 걷는 시간을 직접 말했으면 그 분, 없으면 null */
    static Integer statedWalkMinutes(String text) {
        if (text == null) return null;
        Matcher m = WALK_MINUTES.matcher(text);
        if (!m.find()) return null;
        return Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(4));
    }
}
