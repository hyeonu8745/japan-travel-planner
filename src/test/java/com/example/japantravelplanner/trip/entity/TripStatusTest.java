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
}
