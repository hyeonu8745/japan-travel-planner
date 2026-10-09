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

    @Test
    void 대표_장르는_첫_번째_장르다() {
        assertThat(RestaurantFinder.firstGenre("カフェ,洋食,パン")).isEqualTo("カフェ");
        assertThat(RestaurantFinder.firstGenre(null)).isEmpty();
    }

    @Test
    void 대표_장르가_간식이면_저녁_후보가_아니다() {
        // 실제 응답에서 저녁으로 뽑혔던 곳들 (카페·빵집)
        assertThat(RestaurantFinder.isSnackLed("カフェ,洋食,パン")).isTrue();
        assertThat(RestaurantFinder.isSnackLed("カフェ,洋食,カフェ・喫茶（その他）")).isTrue();
        assertThat(RestaurantFinder.isSnackLed("パン,サンドイッチ,ケーキ")).isTrue();
    }

    @Test
    void 대표_장르가_식사면_간식이_섞여_있어도_후보다() {
        assertThat(RestaurantFinder.isSnackLed("洋食,カフェ")).isFalse();
        assertThat(RestaurantFinder.isSnackLed("ラーメン,餃子")).isFalse();
        assertThat(RestaurantFinder.isSnackLed(null)).isFalse();
    }

    // ── 술이 중심인 바 (10-09: 바가 대표 장르인 곳이 여행 저녁으로 뽑혔다) ──

    @Test
    void 대표_장르가_바이고_식사가_없으면_끼니_후보가_아니다() {
        assertThat(RestaurantFinder.isDrinkLed("バー")).isTrue();                       // 石の華
        assertThat(RestaurantFinder.isDrinkLed("バー,ラウンジ,ワインバー")).isTrue();    // ステラガーデン (호텔 라운지 바)
        assertThat(RestaurantFinder.isDrinkLed("ワインバー,バー")).isTrue();
        assertThat(RestaurantFinder.isDrinkLed("バー・お酒（その他）")).isTrue();
        assertThat(RestaurantFinder.isDrinkLed("バー,カフェ")).isTrue();
    }

    @Test
    void 바라도_두_번째_장르가_식사면_후보로_남긴다() {
        assertThat(RestaurantFinder.isDrinkLed("バー,ビストロ,バル・バール")).isFalse();          // イーグル
        assertThat(RestaurantFinder.isDrinkLed("ダイニングバー,イタリアン,ハンバーガー")).isFalse();
    }

    @Test
    void 이름이_비슷한_식사_장르는_바로_보지_않는다() {
        assertThat(RestaurantFinder.isDrinkLed("ハンバーガー,ステーキ")).isFalse();
        assertThat(RestaurantFinder.isDrinkLed("バーベキュー,焼肉")).isFalse();
        assertThat(RestaurantFinder.isDrinkLed("ジュースバー")).isFalse();
        assertThat(RestaurantFinder.isDrinkLed("ラーメン,ビアバー")).isFalse();   // 대표가 식사
        assertThat(RestaurantFinder.isDrinkLed(null)).isFalse();
    }

    @Test
    void 바르와_바알도_흡연_가능성_장르다() {
        // 실제 응답에서 담배 연기를 싫어하는 여행의 저녁으로 뽑혔던 곳 (炭火焼スペインバルMon)
        assertThat(RestaurantFinder.isSmokyGenre("バル・バール")).isTrue();
        assertThat(RestaurantFinder.isSmokyGenre("スペインバル")).isTrue();
        assertThat(RestaurantFinder.isSmokyGenre("バール")).isTrue();
    }

    @Test
    void 이름이_비슷한_다른_음식은_흡연_장르가_아니다() {
        assertThat(RestaurantFinder.isSmokyGenre("ネパール料理")).isFalse();
        assertThat(RestaurantFinder.isSmokyGenre("ハンバーガー")).isFalse();
        assertThat(RestaurantFinder.isSmokyGenre("ジュースバー")).isFalse();
    }

    @Test
    void 대표_장르는_앞의_두_개() {
        assertThat(RestaurantFinder.mainGenres("そば,うどん,カレーライス")).containsExactlyInAnyOrder("そば", "うどん");
        assertThat(RestaurantFinder.mainGenres("つけ麺")).containsExactly("つけ麺");
        assertThat(RestaurantFinder.mainGenres(null)).isEmpty();
    }
}