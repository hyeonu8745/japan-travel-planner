package com.example.japantravelplanner.itinerary.meal;

import com.example.japantravelplanner.itinerary.dictionary.FoodDictionary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 10-16: 알레르기 — 장르로 거를 수 있는 것은 음식 사전이, 거를 수 없는 재료는 이름을 들어 안내 */
class AllergyWordsTest {

    private final FoodFilterResolver resolver = new FoodFilterResolver(new FoodDictionary());

    @Test
    void 장르로_거를_수_없는_재료를_입력_순서대로_찾는다() {
        assertThat(AllergyWords.unfilterable("우유, 땅콩 알레르기 있어요")).containsExactly("우유", "땅콩");
        assertThat(AllergyWords.unfilterable("참깨")).containsExactly("참깨");      // "깨"가 따로 잡히지 않게
        assertThat(AllergyWords.unfilterable("땅콩이랑 콩")).containsExactly("땅콩", "콩");
        assertThat(AllergyWords.unfilterable("새우, 메밀")).isEmpty();              // 사전이 장르로 거른다
        assertThat(AllergyWords.unfilterable(null)).isEmpty();
    }

    @Test
    void 메밀_알레르기는_소바와_우동집을_뺀다() {
        FoodFilter filter = resolver.resolve(List.of(), "메밀 알레르기");
        assertThat(filter.avoidGenres()).contains("そば", "立ち食いそば", "うどん");
        assertThat(filter.hasAllergies()).isTrue();
        assertThat(filter.unfilteredAllergies()).isEmpty();
    }

    @Test
    void 새우_갑각류_조개_생선은_해산물_튀김_초밥_장르를_뺀다() {
        assertThat(resolver.resolve(List.of(), "새우").avoidGenres()).contains("天ぷら", "寿司", "魚介料理・海鮮料理");
        assertThat(resolver.resolve(List.of(), "갑각류").avoidGenres()).contains("かに", "天丼・天重");
        assertThat(resolver.resolve(List.of(), "조개류").avoidGenres()).contains("オイスターバー");
        assertThat(resolver.resolve(List.of(), "생선").avoidGenres()).contains("寿司", "うなぎ");
    }

    @Test
    void 밀가루_알레르기는_빵_면에_더해_케이크_튀김_오코노미야키도_뺀다() {
        assertThat(resolver.resolve(List.of(), "밀가루").avoidGenres())
                .contains("パン", "うどん", "ケーキ", "パンケーキ", "天ぷら", "お好み焼き");
    }

    @Test
    void 거를_수_없는_재료는_이름을_남기고_알레르기가_없으면_아무것도_없다() {
        FoodFilter filter = resolver.resolve(List.of(), "땅콩, 새우");
        assertThat(filter.unfilteredAllergies()).containsExactly("땅콩");
        assertThat(filter.avoidGenres()).contains("天ぷら");

        FoodFilter none = resolver.resolve(List.of(), "  ");
        assertThat(none.hasAllergies()).isFalse();
        assertThat(none.unfilteredAllergies()).isEmpty();
    }
}
