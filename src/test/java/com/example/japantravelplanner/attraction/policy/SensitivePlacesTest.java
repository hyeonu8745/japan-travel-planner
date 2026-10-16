package com.example.japantravelplanner.attraction.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitivePlacesTest {

    @Test
    void 전범_합사_신사와_침략_관련_장소는_제외한다() {
        assertThat(SensitivePlaces.isExcluded("靖国神社社殿")).isTrue();
        assertThat(SensitivePlaces.isExcluded("靖国神社 中門鳥居")).isTrue();
        assertThat(SensitivePlaces.isExcluded("遊就館")).isTrue();
        assertThat(SensitivePlaces.isExcluded("京都霊山護國神社")).isTrue();
        assertThat(SensitivePlaces.isExcluded("東郷神社")).isTrue();
        assertThat(SensitivePlaces.isExcluded("旧乃木将軍邸")).isTrue();
        assertThat(SensitivePlaces.isExcluded("豊国神社")).isTrue();
        assertThat(SensitivePlaces.isExcluded("耳塚")).isTrue();
    }

    @Test
    void 이름이_비슷한_다른_곳과_대표_관광지는_남긴다() {
        assertThat(SensitivePlaces.isExcluded("護国寺")).isFalse();          // 분쿄구의 절
        assertThat(SensitivePlaces.isExcluded("大阪城")).isFalse();          // 안내만(빼지 않음)
        assertThat(SensitivePlaces.isExcluded("上野恩賜公園 西郷隆盛像")).isFalse();
        assertThat(SensitivePlaces.isExcluded("浅草寺")).isFalse();
        assertThat(SensitivePlaces.isExcluded(null)).isFalse();
    }

    @Test
    void 메이지_신궁과_안의_시설은_빼고_외원_야구장과_이름만_같은_곳은_남긴다() {
        assertThat(SensitivePlaces.isExcluded("明治神宮")).isTrue();
        assertThat(SensitivePlaces.isExcluded("明治神宮御苑")).isTrue();
        assertThat(SensitivePlaces.isExcluded("明治神宮 二の鳥居")).isTrue();
        assertThat(SensitivePlaces.isExcluded("明治神宮ミュージアム")).isTrue();
        assertThat(SensitivePlaces.isExcluded("明治神宮聖徳記念絵画館")).isTrue();
        assertThat(SensitivePlaces.isExcluded("Meiji Jingu")).isTrue();
        assertThat(SensitivePlaces.isExcluded("Meiji Shrine")).isTrue();

        assertThat(SensitivePlaces.isExcluded("明治神宮外苑 いちょう並木")).isFalse();
        assertThat(SensitivePlaces.isExcluded("明治神宮野球場")).isFalse();
        assertThat(SensitivePlaces.isExcluded("Meiji Jingu Gaien")).isFalse();
        assertThat(SensitivePlaces.isExcluded("Meiji Jingu Stadium")).isFalse();
        assertThat(SensitivePlaces.isExcluded("明治大学博物館")).isFalse();
        assertThat(SensitivePlaces.isExcluded("明治生命館")).isFalse();
        assertThat(SensitivePlaces.isExcluded("都立明治公園")).isFalse();
        assertThat(SensitivePlaces.isExcluded("明治丸海事ミュージアム")).isFalse();
    }

    @Test
    void 데이터에서_찾은_군인_옛집_가토_기요마사_전쟁_기념_시설도_뺀다() {
        assertThat(SensitivePlaces.isExcluded("乃木公園")).isTrue();
        assertThat(SensitivePlaces.isExcluded("東郷公園")).isTrue();
        assertThat(SensitivePlaces.isExcluded("清正公寺")).isTrue();
        assertThat(SensitivePlaces.isExcluded("清正井")).isTrue();
        assertThat(SensitivePlaces.isExcluded("北海道神宮頓宮")).isTrue();
        assertThat(SensitivePlaces.isExcluded("Hokkaido Jingu")).isTrue();
        assertThat(SensitivePlaces.isExcluded("昭和館")).isTrue();
        assertThat(SensitivePlaces.isExcluded("しょうけい館（戦傷病者史料館）")).isTrue();
        assertThat(SensitivePlaces.isExcluded("二・二六事件慰霊像")).isTrue();
        assertThat(SensitivePlaces.isExcluded("福澤諭吉記念 慶應義塾史展示館")).isTrue();
    }

    @Test
    void 쇼와_레트로_박물관과_피해를_알리는_곳은_남긴다() {
        assertThat(SensitivePlaces.isExcluded("高山昭和館")).isFalse();
        assertThat(SensitivePlaces.isExcluded("湯布院昭和館")).isFalse();
        assertThat(SensitivePlaces.isExcluded("Takayama Shōwa-kan")).isFalse();
        assertThat(SensitivePlaces.isExcluded("アクティブ・ミュージアム「女たちの戦争と平和資料館」")).isFalse();
        assertThat(SensitivePlaces.isExcluded("韓国人原爆犠牲者慰霊碑")).isFalse();
        assertThat(SensitivePlaces.isExcluded("東京都慰霊堂")).isFalse();
        assertThat(SensitivePlaces.isExcluded("皇居東御苑")).isFalse();
        assertThat(SensitivePlaces.isExcluded("湊川神社")).isFalse();
        assertThat(SensitivePlaces.isExcluded("乃木坂")).isFalse();
    }
}
