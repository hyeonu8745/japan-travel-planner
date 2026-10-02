package com.example.japantravelplanner.itinerary.dictionary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceTypeLabelsTest {

    private final PlaceTypeLabels labels = new PlaceTypeLabels();

    @Test
    void 영어_type을_한국어로_바꾼다() {
        assertThat(labels.toVisibleLabels("buddhist_temple")).containsExactly("사찰");
    }

    @Test
    void 의미_없는_공통_type은_보여주지_않는다() {
        // 센소지의 실제 types
        assertThat(labels.toVisibleLabels(
                "buddhist_temple,tourist_attraction,place_of_worship,association_or_organization,point_of_interest,establishment"))
                .contains("사찰")
                .doesNotContain("point_of_interest", "establishment");
    }

    @Test
    void 사전에_없는_type과_빈_값은_무시한다() {
        assertThat(labels.toVisibleLabels("unknown_type")).isEmpty();
        assertThat(labels.toVisibleLabels(null)).isEmpty();
    }
}