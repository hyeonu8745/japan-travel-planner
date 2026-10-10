package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 걷는 기준과 가까운 곳 위주 여부 (10-10: 기본 30분, 가까운 곳을 원하면 예전처럼) */
class TravelOptionsTest {

    private final TravelProperties props = new TravelProperties();

    private static Trip trip(TravelPace pace, String mobility) {
        return Trip.builder().travelPace(pace).transportMode(TransportMode.PUBLIC_TRANSIT)
                .mobilityConstraints(mobility).build();
    }

    private static List<PreferenceConstraint> mobility(String text) {
        return List.of(new PreferenceConstraint(ConstraintCategory.MOBILITY, text));
    }

    @Test
    void 기본은_30분까지_걷고_지역_안에서_넓게() {
        TravelOptions options = TravelOptions.of(trip(TravelPace.NORMAL, null), List.of(), props);

        assertThat(options.mode()).isEqualTo(LegMode.TRANSIT);
        assertThat(options.walkThresholdMinutes()).isEqualTo(30);
        assertThat(options.compact()).isFalse();
    }

    @Test
    void 여유_속도나_조건_입력의_이동_제약이면_짧게_가까운_곳_위주() {
        assertThat(TravelOptions.of(trip(TravelPace.RELAXED, null), List.of(), props))
                .isEqualTo(new TravelOptions(LegMode.TRANSIT, 10, true));
        assertThat(TravelOptions.of(trip(TravelPace.NORMAL, "무릎이 안 좋아요"), List.of(), props))
                .isEqualTo(new TravelOptions(LegMode.TRANSIT, 10, true));
    }

    @Test
    void 대화에서_걷는_시간을_말하면_그_시간() {
        // 10-10 여행 8: 이동 조건이 있다는 이유만으로 도보 10분이 됐던 문장
        TravelOptions options = TravelOptions.of(trip(TravelPace.NORMAL, null),
                mobility("멀면 대중교통 타고 30분 정도의 거리면 걸어서 다니고 싶어"), props);

        assertThat(options.walkThresholdMinutes()).isEqualTo(30);
        assertThat(options.compact()).isFalse();
    }

    @Test
    void 말한_시간이_기본보다_짧으면_가까운_곳_위주이고_범위를_벗어나면_자른다() {
        assertThat(TravelOptions.of(trip(TravelPace.NORMAL, null), mobility("도보 15분 이내만"), props))
                .isEqualTo(new TravelOptions(LegMode.TRANSIT, 15, true));
        assertThat(TravelOptions.of(trip(TravelPace.NORMAL, null), mobility("한 시간 넘게도 걸을 수 있어요, 60분 걸어도 돼요"), props)
                .walkThresholdMinutes()).isEqualTo(40);
    }

    @Test
    void 대화에서_가까운_곳이나_적게_걷기를_원하면_짧게() {
        assertThat(TravelOptions.of(trip(TravelPace.NORMAL, null), mobility("가까운 곳 위주로 다니고 싶어요"), props))
                .isEqualTo(new TravelOptions(LegMode.TRANSIT, 10, true));
        assertThat(TravelOptions.of(trip(TravelPace.NORMAL, null), mobility("계단은 피하고 싶어요"), props).compact()).isTrue();
    }

    @Test
    void 걷는_것과_상관없는_이동_조건은_기본() {
        assertThat(TravelOptions.of(trip(TravelPace.NORMAL, null), mobility("택시는 타지 않아요"), props))
                .isEqualTo(new TravelOptions(LegMode.TRANSIT, 30, false));
    }

    @Test
    void 렌트카면_운전() {
        Trip car = Trip.builder().travelPace(TravelPace.NORMAL).transportMode(TransportMode.RENTAL_CAR).build();
        assertThat(TravelOptions.of(car, List.of(), props).mode()).isEqualTo(LegMode.DRIVE);
    }
}
