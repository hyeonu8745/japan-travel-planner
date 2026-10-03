package com.example.japantravelplanner.itinerary.candidate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttractionScorerTest {

    private static final double MEAN = 4.3;
    private static final int PRIOR = 200;

    @Test
    void 리뷰가_적으면_전체_평균에_가까워진다() {
        double godzilla = AttractionScorer.bayesian(5.0, 2, MEAN, PRIOR);       // 리뷰 2개, 5.0
        assertThat(godzilla).isBetween(4.30, 4.32);
    }

    @Test
    void 리뷰가_많으면_자기_평점에_가깝다() {
        double sensoji = AttractionScorer.bayesian(4.6, 99_070, MEAN, PRIOR);   // 리뷰 10만, 4.6
        assertThat(sensoji).isBetween(4.59, 4.61);
    }

    @Test
    void 리뷰가_많은_곳이_리뷰가_적은_고평점보다_위에_온다() {
        double gyoenSakura = AttractionScorer.bayesian(4.7, 395, MEAN, PRIOR);  // 신주쿠교엔 벚꽃 정원
        double sensoji = AttractionScorer.bayesian(4.6, 99_070, MEAN, PRIOR);
        double godzilla = AttractionScorer.bayesian(5.0, 2, MEAN, PRIOR);

        assertThat(sensoji).isGreaterThan(gyoenSakura);   // 12-1편에서 발견한 문제 해결
        assertThat(gyoenSakura).isGreaterThan(godzilla);
    }

    @Test
    void 같은_건물이나_공원의_일부는_중복으로_본다() {
        assertThat(AttractionScorer.isNearDuplicate(
                "東京都庁", 35.6894807, 139.6916863,
                "東京都庁第一本庁舎 北展望室", 35.689733, 139.6916563)).isTrue();
        assertThat(AttractionScorer.isNearDuplicate(
                "新宿御苑", 35.6851763, 139.7100517,
                "新宿御苑 桜園地", 35.6822873, 139.7101213)).isTrue();
    }

    @Test
    void 가까워도_이름이_다르면_다른_관광지다() {
        // 센소지와 아사쿠사 신사는 바로 옆이지만 다른 곳
        assertThat(AttractionScorer.isNearDuplicate(
                "浅草寺", 35.7147651, 139.7966553,
                "浅草神社", 35.7151665, 139.7974389)).isFalse();
    }

    @Test
    void 큰_공원은_수백_m_떨어져도_같은_장소다() {
        // 신주쿠교엔 대표 좌표와 벚꽃 정원은 약 320m
        assertThat(AttractionScorer.isNearDuplicate(
                "新宿御苑", 35.6851763, 139.7100517,
                "新宿御苑 桜園地", 35.6822873, 139.7101213)).isTrue();
    }
}