package com.example.japantravelplanner.poster;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 포스터 옵션 (10-14): 스타일 고르기, 캐릭터 설명 정리 */
class PosterOptionsTest {

    @Test
    void 본문이_없거나_비어_있으면_수채화에_캐릭터_없음() {
        assertThat(PosterOptions.from(null)).isEqualTo(PosterOptions.DEFAULT);
        assertThat(PosterOptions.from(new PosterRequestDto(" ", "  "))).isEqualTo(PosterOptions.DEFAULT);
    }

    @Test
    void 스타일은_대소문자_상관없이_모르는_값은_400() {
        assertThat(PosterOptions.from(new PosterRequestDto("retro", null)).style()).isEqualTo(PosterStyle.RETRO);
        assertThat(PosterOptions.from(new PosterRequestDto("STICKER", null)).style()).isEqualTo(PosterStyle.STICKER);
        assertThatThrownBy(() -> PosterOptions.from(new PosterRequestDto("anime", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("WATERCOLOR");
    }

    @Test
    void 캐릭터는_따옴표와_줄바꿈을_지우고_50자를_넘으면_400() {
        assertThat(PosterOptions.cleanCharacter("  \"빨간 모자\" 쓴\n토끼 ")).isEqualTo("빨간 모자 쓴 토끼");
        assertThat(PosterOptions.cleanCharacter("가".repeat(50))).hasSize(50);
        assertThatThrownBy(() -> PosterOptions.cleanCharacter("가".repeat(51)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("50자");
    }
}
