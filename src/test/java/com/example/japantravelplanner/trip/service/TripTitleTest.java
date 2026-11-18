package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.trip.region.City;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 기본 제목: 도시 + 박·일 (11-18: 숙소 도시가 정해지면 그 도시 이름) */
class TripTitleTest {

    @Test
    void 도시_이름과_날짜로_기본_제목을_만든다() {
        LocalDate d1 = LocalDate.of(2026, 12, 1);
        assertThat(TripService.defaultTitle(d1, d1.plusDays(3), City.OSAKA)).isEqualTo("오사카 3박 4일");
        assertThat(TripService.defaultTitle(d1, d1, City.KYOTO)).isEqualTo("교토 당일치기");
        assertThat(TripService.defaultTitle(null, null, City.NAHA)).isEqualTo("나하 여행");
        assertThat(TripService.defaultTitle(d1, d1.plusDays(1))).isEqualTo("도쿄 1박 2일");
    }
}
