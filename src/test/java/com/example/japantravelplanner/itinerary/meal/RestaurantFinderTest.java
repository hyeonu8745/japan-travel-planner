package com.example.japantravelplanner.itinerary.meal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantFinderTest {

    @Test
    void 바와_이자카야는_흡연_가능성_장르다() {
        assertThat(RestaurantFinder.isSmokyGenre("バー")).isTrue();
        assertThat(RestaurantFinder.isSmokyGenre("ダイニングバー")).isTrue();
        assertThat(RestaurantFinder.isSmokyGenre("居酒屋")).isTrue();
    }

    @Test
    void 햄버거는_바가_아니다() {
        // "ハンバーガー"에도 "バー"가 들어 있지만 끝이 "ガー"
        assertThat(RestaurantFinder.isSmokyGenre("ハンバーガー")).isFalse();
    }

    @Test
    void 장르_문자열을_낱개로_나눈다() {
        assertThat(RestaurantFinder.genresOf("焼肉,ホルモン")).containsExactlyInAnyOrder("焼肉", "ホルモン");
        assertThat(RestaurantFinder.genresOf(null)).isEmpty();
    }

    @Test
    void 디저트만_파는_곳은_식사_후보가_아니다() {
        assertThat(RestaurantFinder.isSnackOnly(Set.of("和菓子", "どら焼き", "大福"))).isTrue();
        assertThat(RestaurantFinder.isSnackOnly(Set.of("フルーツパーラー", "パフェ", "かき氷"))).isTrue();
    }

    @Test
    void 카페라도_식사가_섞이면_후보다() {
        assertThat(RestaurantFinder.isSnackOnly(Set.of("カフェ", "パスタ"))).isFalse();
        assertThat(RestaurantFinder.isSnackOnly(Set.of("ラーメン", "つけ麺", "餃子"))).isFalse();
    }

    @Test
    void 주스바는_흡연_장르가_아니다() {
        assertThat(RestaurantFinder.isSmokyGenre("ジュースバー")).isFalse();
    }
}