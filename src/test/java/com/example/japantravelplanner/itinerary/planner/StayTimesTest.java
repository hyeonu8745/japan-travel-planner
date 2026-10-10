package com.example.japantravelplanner.itinerary.planner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StayTimesTest {

    @Test
    void 종류별_기본_체류_시간() {
        assertThat(StayTimes.baseOf("museum,tourist_attraction")).isEqualTo(90);
        assertThat(StayTimes.baseOf("buddhist_temple,place_of_worship")).isEqualTo(50);
        assertThat(StayTimes.baseOf("park,tourist_attraction")).isEqualTo(75);
        assertThat(StayTimes.baseOf("market,tourist_attraction")).isEqualTo(90);
        assertThat(StayTimes.baseOf("amusement_park")).isEqualTo(120);
        assertThat(StayTimes.baseOf("tourist_attraction")).isEqualTo(40);
    }

    @Test
    void 전망대는_다른_태그가_붙어도_전망대() {
        // 도쿄타워: shopping_mall이 같이 붙어 있지만 전망대 60분
        assertThat(StayTimes.baseOf("shopping_mall,observation_deck,tourist_attraction")).isEqualTo(60);
    }

    @Test
    void 저녁_뒤_야경은_전망대만_낮과_같고_나머지는_짧게() {
        assertThat(StayTimes.nightBaseOf("observation_deck,landmark")).isEqualTo(60);
        assertThat(StayTimes.nightBaseOf("park,tourist_attraction")).isEqualTo(StayTimes.NIGHT_VIEW);   // 낮이면 75
        assertThat(StayTimes.nightBaseOf("buddhist_temple")).isEqualTo(StayTimes.NIGHT_VIEW);
        assertThat(StayTimes.nightBaseOf(null)).isEqualTo(StayTimes.NIGHT_VIEW);
    }

    @Test
    void 종류를_모르면_기타() {
        assertThat(StayTimes.baseOf(null)).isEqualTo(40);
        assertThat(StayTimes.baseOf("")).isEqualTo(40);
        assertThat(StayTimes.baseOf("point_of_interest")).isEqualTo(40);
    }

    @Test
    void 속도_배율을_곱하고_5분_단위로_반올림() {
        assertThat(StayTimes.scaled(75, 0.85)).isEqualTo(65);   // 63.75 → 65
        assertThat(StayTimes.scaled(50, 1.3)).isEqualTo(65);    // 65.0
        assertThat(StayTimes.scaled(40, 1.0)).isEqualTo(40);
        assertThat(StayTimes.scaled(90, 0.85)).isEqualTo(75);   // 76.5 → 75
    }

    @Test
    void 끼니도_배율을_받는다() {
        assertThat(StayTimes.scaled(StayTimes.LUNCH, 1.3)).isEqualTo(80);    // 78 → 80
        assertThat(StayTimes.scaled(StayTimes.DINNER, 0.85)).isEqualTo(75);  // 76.5 → 75
    }
}