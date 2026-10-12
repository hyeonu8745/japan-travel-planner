package com.example.japantravelplanner.trip.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 10-12: 일정 [완성] 확인 흐름의 상태 규칙 */
class TripStatusTest {

    @Test
    void 완성_전_확인_대기에서_고치면_확인_대기를_유지한다() {
        Trip trip = Trip.builder().status(TripStatus.AWAITING_CONFIRMATION).build();

        trip.markEdited();

        assertThat(trip.getStatus()).isEqualTo(TripStatus.AWAITING_CONFIRMATION);
        assertThat(trip.isAwaitingConfirmation()).isTrue();
    }

    @Test
    void 완성한_뒤에_고치면_EDITING() {
        Trip generated = Trip.builder().status(TripStatus.GENERATED).build();
        Trip editing = Trip.builder().status(TripStatus.EDITING).build();

        generated.markEdited();
        editing.markEdited();

        assertThat(generated.getStatus()).isEqualTo(TripStatus.EDITING);
        assertThat(editing.getStatus()).isEqualTo(TripStatus.EDITING);
        assertThat(generated.isAwaitingConfirmation()).isFalse();
    }

    @Test
    void 바꾼_식당을_기억하고_다시_추천해도_되면_푼다() {
        Trip trip = Trip.builder().build();

        trip.excludeRestaurant(21L);
        trip.excludeRestaurant(null);

        assertThat(trip.excludedRestaurants()).containsExactly(21L);
        assertThat(trip.allowRestaurant(21L)).isTrue();
        assertThat(trip.allowRestaurant(21L)).isFalse();
        assertThat(trip.excludedRestaurants()).isEmpty();
    }

    @Test
    void 일정을_만들_때_바꾼_식당은_처음부터_고르지_않는다() {
        var history = new com.example.japantravelplanner.itinerary.planner.MealHistory(java.util.Set.of(21L));
        assertThat(history.usedIds()).containsExactly(21L);
    }
}
