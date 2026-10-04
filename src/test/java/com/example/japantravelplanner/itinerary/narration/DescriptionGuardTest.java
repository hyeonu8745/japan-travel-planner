package com.example.japantravelplanner.itinerary.narration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionGuardTest {

    @Test
    void 평범한_설명은_통과하고_공백만_정리된다() {
        String text = "신주쿠에서 하루를 시작해요.\n  정원을 걷고 츠케멘으로 저녁을 먹은 뒤 야경으로 마무리해요.";
        assertThat(DescriptionGuard.clean(text))
                .isEqualTo("신주쿠에서 하루를 시작해요. 정원을 걷고 츠케멘으로 저녁을 먹은 뒤 야경으로 마무리해요.");
    }

    @Test
    void 가격과_요금_이야기는_버린다() {
        assertThat(DescriptionGuard.clean("입장료가 없어서 부담 없이 들를 수 있어요.")).isNull();
        assertThat(DescriptionGuard.clean("1,000엔이면 충분히 즐길 수 있어요.")).isNull();
        assertThat(DescriptionGuard.clean("한 끼에 3000円 정도예요.")).isNull();
    }

    @Test
    void 영업_운영_정보는_버린다() {
        assertThat(DescriptionGuard.clean("영업시간이 길어서 여유로워요.")).isNull();
        assertThat(DescriptionGuard.clean("월요일은 휴무일 수 있어요.")).isNull();
        assertThat(DescriptionGuard.clean("인기 가게라 예약 필수예요.")).isNull();
    }

    @Test
    void 숫자_시각과_이동_시간은_버린다() {
        assertThat(DescriptionGuard.clean("오후 3시에 정원을 둘러봐요.")).isNull();
        assertThat(DescriptionGuard.clean("18:00에 저녁을 먹어요.")).isNull();
        assertThat(DescriptionGuard.clean("역에서 10분만 걸으면 도착해요.")).isNull();
    }

    @Test
    void 시간대_표현과_분위기_같은_말은_허용한다() {
        assertThat(DescriptionGuard.clean("점심 뒤에는 2시간 정도 여유롭게 걸어요.")).isNotNull();
        assertThat(DescriptionGuard.clean("차분한 분위기의 정원이에요.")).isNotNull();
        assertThat(DescriptionGuard.clean("오전에는 사찰, 저녁에는 야경이에요.")).isNotNull();
    }

    @Test
    void 링크와_태그는_버린다() {
        assertThat(DescriptionGuard.clean("자세한 내용은 https://example.com 에서 확인하세요.")).isNull();
        assertThat(DescriptionGuard.clean("<b>멋진</b> 하루예요.")).isNull();
    }

    @Test
    void 비었거나_너무_길면_버린다() {
        assertThat(DescriptionGuard.clean(null)).isNull();
        assertThat(DescriptionGuard.clean("   \n ")).isNull();
        assertThat(DescriptionGuard.clean("가".repeat(DescriptionGuard.MAX_CHARS + 1))).isNull();
        assertThat(DescriptionGuard.clean("가".repeat(DescriptionGuard.MAX_CHARS))).isNotNull();
    }
}