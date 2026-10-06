package com.example.japantravelplanner.itinerary.meal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 타베로그 정기 휴일 문구 읽기. 사례는 실제 응답에 나온 문구와 판단하기 어려운 문구 */
class RegularHolidayTest {

    @Test
    void 요일_하나() {
        assertThat(RegularHoliday.closedDays("日曜日")).containsExactly(DayOfWeek.SUNDAY);
    }

    @Test
    void 요일을_나열하면_모두_읽는다() {
        assertThat(RegularHoliday.closedDays("月曜日、金曜日"))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(RegularHoliday.closedDays("日曜、月曜"))
                .containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.MONDAY);
    }

    @Test
    void 공휴일은_요일만_읽고_공휴일은_판단하지_않는다() {
        assertThat(RegularHoliday.closedDays("土曜日、日曜日、祝日"))
                .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(RegularHoliday.closedDays("土日祝休み"))
                .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(RegularHoliday.closedDays("土日祝"))
                .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @ParameterizedTest
    @CsvSource({
            "日曜定休, SUNDAY",
            "毎週水曜日, WEDNESDAY",
            "木曜日定休日, THURSDAY",
            "火曜日(祝日の場合は営業), TUESDAY",
            "月曜日（祝日の場合は翌日）, MONDAY"
    })
    void 흔한_표현과_괄호_속_부가_설명(String text, DayOfWeek expected) {
        assertThat(RegularHoliday.closedDays(text)).containsExactly(expected);
    }

    @Test
    void 쉬는_날이_없다는_문구는_빈_집합() {
        assertThat(RegularHoliday.closedDays("無休")).isEmpty();
        assertThat(RegularHoliday.closedDays("年中無休")).isEmpty();
        assertThat(RegularHoliday.closedDays("定休日なし")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "不定休",                    // 비정기
            "水曜日、不定休",             // 확실한 요일이 있어도 비정기가 섞이면 판단하지 않는다
            "第2・第4月曜日",             // 몇째 주: 매주 월요일이 아니다
            "木曜日、第1・第3水曜日",
            "月曜日～水曜日",             // 범위
            "月曜日以外",                // 월요일 이외가 휴일인지 영업일인지 알 수 없다
            "年末年始"                   // 요일 없음
    })
    void 판단하기_어려운_문구는_쉬는_날로_보지_않는다(String text) {
        assertThat(RegularHoliday.closedDays(text)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ご確認ください", "Mon"})
    void 비어_있거나_모르는_문구는_쉬는_날로_보지_않는다(String text) {
        assertThat(RegularHoliday.closedDays(text)).isEmpty();
    }

    @Test
    void 전각_괄호와_숫자도_읽는다() {
        assertThat(RegularHoliday.closedDays("水曜日（第3を除く）")).isEmpty();   // 第, 除く가 있으면 판단하지 않는다
        assertThat(RegularHoliday.closedDays("金曜日（祝日営業）")).containsExactly(DayOfWeek.FRIDAY);
    }

    @Test
    void 실제_사례_금요일_일정에_월요일_금요일_휴무_식당은_휴일이다() {
        // DAY4 2026-10-23(금요일)의 점심 スパイスポスト: 月曜日、金曜日
        assertThat(RegularHoliday.isClosedOn("月曜日、金曜日", LocalDate.of(2026, 10, 23))).isTrue();
    }

    @Test
    void 다른_요일이면_휴일이_아니다() {
        // 2026-10-21 수요일, 2026-10-22 목요일
        assertThat(RegularHoliday.isClosedOn("土曜日、日曜日、祝日", LocalDate.of(2026, 10, 21))).isFalse();
        assertThat(RegularHoliday.isClosedOn("日曜、月曜", LocalDate.of(2026, 10, 22))).isFalse();
        assertThat(RegularHoliday.isClosedOn("無休", LocalDate.of(2026, 10, 20))).isFalse();
    }

    @Test
    void 날짜가_없으면_휴일로_보지_않는다() {
        assertThat(RegularHoliday.isClosedOn("日曜日", null)).isFalse();
    }
}