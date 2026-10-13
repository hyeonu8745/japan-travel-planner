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
}
