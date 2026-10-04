package com.example.japantravelplanner.itinerary.planner;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DayWindowTest {

    @Test
    void 중간_날은_기본_시간에_두_끼() {
        DayWindow w = DayWindow.of(false, false, LocalTime.of(15, 0), LocalTime.of(19, 0), false);
        assertThat(w.start()).isEqualTo(LocalTime.of(9, 30));
        assertThat(w.end()).isEqualTo(LocalTime.of(21, 0));
        assertThat(w.lunch()).isTrue();
        assertThat(w.dinner()).isTrue();
    }

    @Test
    void 야경_관심사면_22시까지() {
        assertThat(DayWindow.of(false, false, null, null, true).end()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void 첫날_15시_도착이면_16시부터_저녁만() {
        DayWindow w = DayWindow.of(true, false, LocalTime.of(15, 0), null, true);
        assertThat(w.start()).isEqualTo(LocalTime.of(16, 0));
        assertThat(w.lunch()).isFalse();
        assertThat(w.dinner()).isTrue();
    }

    @Test
    void 첫날_아침_일찍_도착해도_09시30분부터() {
        DayWindow w = DayWindow.of(true, false, LocalTime.of(7, 0), null, false);
        assertThat(w.start()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void 마지막_날_19시_출발이면_16시까지_점심만() {
        DayWindow w = DayWindow.of(false, true, null, LocalTime.of(19, 0), true);
        assertThat(w.end()).isEqualTo(LocalTime.of(16, 0));
        assertThat(w.lunch()).isTrue();
        assertThat(w.dinner()).isFalse();
    }

    @Test
    void 도착_시각을_모르면_기본값() {
        DayWindow w = DayWindow.of(true, true, null, null, false);
        assertThat(w.start()).isEqualTo(LocalTime.of(9, 30));
        assertThat(w.end()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void 밤늦게_도착하면_일정이_없는_날() {
        DayWindow w = DayWindow.of(true, false, LocalTime.of(23, 30), null, false);
        assertThat(w.isEmpty()).isTrue();
        assertThat(w.dinner()).isFalse();
    }

    @Test
    void 당일치기에_오전_출발이면_일정이_없다() {
        DayWindow w = DayWindow.of(true, true, LocalTime.of(10, 0), LocalTime.of(12, 0), false);
        assertThat(w.isEmpty()).isTrue();
    }
}