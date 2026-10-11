package com.example.japantravelplanner.itinerary.dictionary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FoodDictionaryTest {

    // 실제 resources/data/food_dictionary.csv를 읽는다
    private final FoodDictionary dictionary = new FoodDictionary();

    @Test
    void 표현_하나로_장르를_찾는다() {
        assertThat(dictionary.genresOf("라멘", FoodPreferenceType.PREFER)).contains("ラーメン");
    }

    @Test
    void 문장_안의_여러_표현을_모두_찾는다() {
        assertThat(dictionary.findGenres("라멘이랑 야키토리 좋아해요", FoodPreferenceType.PREFER))
                .contains("ラーメン", "焼鳥");
    }

    @Test
    void 피하고_싶은_음식은_AVOID로_찾는다() {
        assertThat(dictionary.findGenres("회는 못 먹어요", FoodPreferenceType.AVOID))
                .contains("寿司");
    }

    @Test
    void PREFER와_AVOID는_서로_다른_사전에서_찾는다() {
        // "밀가루"는 피할 때만 쓰는 표현이라 AVOID 사전에만 있음
        assertThat(dictionary.findGenres("밀가루", FoodPreferenceType.AVOID)).isNotEmpty();
        assertThat(dictionary.findGenres("밀가루", FoodPreferenceType.PREFER)).isEmpty();
    }

    @Test
    void 좋아하는지_피하는지는_부르는_쪽이_정한다() {
        // "회"는 양쪽 사전에 다 있음. 문장의 부정 표현은 읽지 않고,
        // AI가 정리한 카테고리(FOOD_PREFER / FOOD_AVOID)에 따라 부르는 쪽이 종류를 고른다
        assertThat(dictionary.findGenres("회 피함", FoodPreferenceType.AVOID)).contains("寿司");
        assertThat(dictionary.findGenres("회 좋아함", FoodPreferenceType.PREFER)).contains("寿司");
    }

    @Test
    void 사전에_없는_표현이면_빈_결과() {
        assertThat(dictionary.findGenres("아무거나 괜찮아요", FoodPreferenceType.PREFER)).isEmpty();
        assertThat(dictionary.findGenres(null, FoodPreferenceType.PREFER)).isEmpty();
    }

    @Test
    void 장어덮밥은_덮밥_전체가_아니라_장어만() {
        // 10-11 채팅 수정: "장어덮밥"이 "장어" + "덮밥"으로 나뉘어 모든 덮밥집이 후보가 됐다
        assertThat(new FoodDictionary().findGenres("장어덮밥", FoodPreferenceType.PREFER))
                .containsExactly("うなぎ");
    }
}
