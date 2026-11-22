package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 날짜로 문을 닫는 관광지 (11-22) */
class SeasonalClosuresTest {

    private static Attraction named(String name, String openingHours) {
        return Attraction.builder().id(1L).name(name).openingHours(openingHours).build();
    }

    @Test
    void 와카쿠사야마는_3월_셋째_토요일부터_12월_둘째_일요일까지만_오른다() {
        Attraction wakakusa = named("若草山", null);
        // 2026: 3월 21일(셋째 토) ~ 12월 13일(둘째 일)
        assertThat(SeasonalClosures.closedOn(wakakusa, LocalDate.of(2026, 12, 10))).isFalse();
        assertThat(SeasonalClosures.closedOn(wakakusa, LocalDate.of(2026, 12, 13))).isFalse();
        assertThat(SeasonalClosures.closedOn(wakakusa, LocalDate.of(2026, 12, 14))).isTrue();
        assertThat(SeasonalClosures.closedOn(wakakusa, LocalDate.of(2027, 1, 20))).isTrue();
        assertThat(SeasonalClosures.closedOn(wakakusa, LocalDate.of(2026, 3, 20))).isTrue();
        assertThat(SeasonalClosures.closedOn(wakakusa, LocalDate.of(2026, 3, 21))).isFalse();
    }

    @Test
    void 영업시간의_연말연시_같은_날짜_휴무를_읽는다() {
        String museum = "Mo off; Tu-Su 09:30-17:00; Dec 28-Jan 01 off";
        assertThat(SeasonalClosures.closedByOpeningHours(museum, LocalDate.of(2026, 12, 30))).isTrue();
        assertThat(SeasonalClosures.closedByOpeningHours(museum, LocalDate.of(2027, 1, 1))).isTrue();
        assertThat(SeasonalClosures.closedByOpeningHours(museum, LocalDate.of(2027, 1, 2))).isFalse();
        // 요일 휴무는 보지 않는다
        assertThat(SeasonalClosures.closedByOpeningHours(museum, LocalDate.of(2026, 12, 7))).isFalse();

        assertThat(SeasonalClosures.closedByOpeningHours("Mo-Su 06:00-18:00; Dec 29-31 off", LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(SeasonalClosures.closedByOpeningHours("Mo-Su 08:45-17:00; Jan 01-03 off", LocalDate.of(2027, 1, 2))).isTrue();
        assertThat(SeasonalClosures.closedByOpeningHours("Tu-Su 10:00-17:00; Aug 10-20, Dec 24-Jan 07 off", LocalDate.of(2026, 8, 15))).isTrue();
        assertThat(SeasonalClosures.closedByOpeningHours("Tu-Su 10:00-17:00; Aug 10-20, Dec 24-Jan 07 off", LocalDate.of(2027, 1, 5))).isTrue();
        assertThat(SeasonalClosures.closedByOpeningHours("Mo-Su 08:15-20:30; Dec29-Jan 03 off", LocalDate.of(2026, 12, 30))).isTrue();
    }

    @Test
    void 너무_긴_날짜_휴무는_잘못_적힌_것으로_보고_무시한다() {
        // 石川県政記念しいのき迎賓館: "Dec 29-Jun 03 off" (반년 휴무로 읽히지만 실제로는 연말연시)
        assertThat(SeasonalClosures.closedByOpeningHours("Mo-Su 09:00-22:00; Dec 29-Jun 03 off", LocalDate.of(2027, 4, 1))).isFalse();
        assertThat(SeasonalClosures.closedByOpeningHours(null, LocalDate.of(2027, 4, 1))).isFalse();
        assertThat(SeasonalClosures.closedOn(named("清水寺", "Mo-Su 06:00-18:00"), LocalDate.of(2026, 12, 10))).isFalse();
    }
}
