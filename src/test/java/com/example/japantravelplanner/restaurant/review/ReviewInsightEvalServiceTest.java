package com.example.japantravelplanner.restaurant.review;

import com.example.japantravelplanner.restaurant.review.ReviewInsightEvalService.EvalReport;
import com.example.japantravelplanner.restaurant.review.ReviewInsightEvalService.TagScore;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 정확도 계산 (AI 없이, 예측을 직접 넣어서) */
class ReviewInsightEvalServiceTest {

    private static TagScore score(EvalReport report, ReviewTag tag) {
        return report.scores().stream().filter(s -> s.tag().equals(tag.name())).findFirst().orElseThrow();
    }

    @Test
    void 맞힌_것_잘못_붙인_것_놓친_것으로_정밀도와_재현율을_낸다() {
        List<ReviewInsightEvalSamples> samples = List.of(
                ReviewInsightEvalSamples.of("외국인이라고 무시", Set.of(ReviewTag.DISCRIMINATION)),
                ReviewInsightEvalSamples.of("직원 태도 별로", Set.of(ReviewTag.UNFRIENDLY)),
                ReviewInsightEvalSamples.of("가격이 비싸요", Set.of()));
        List<Set<ReviewTag>> predicted = List.of(
                EnumSet.of(ReviewTag.DISCRIMINATION),          // 맞힘
                EnumSet.noneOf(ReviewTag.class),               // 불친절 놓침
                EnumSet.of(ReviewTag.HYGIENE));                // 위생 잘못 붙임

        EvalReport report = ReviewInsightEvalService.report(samples, predicted, 0);

        assertThat(score(report, ReviewTag.DISCRIMINATION).precision()).isEqualTo(1.0);
        assertThat(score(report, ReviewTag.DISCRIMINATION).recall()).isEqualTo(1.0);
        assertThat(score(report, ReviewTag.UNFRIENDLY).recall()).isEqualTo(0.0);
        assertThat(score(report, ReviewTag.UNFRIENDLY).precision()).isNull();   // 붙인 게 없다
        assertThat(score(report, ReviewTag.HYGIENE).precision()).isEqualTo(0.0);
        assertThat(report.mistakes()).hasSize(2);
    }

    @Test
    void 허용한_종류는_잘못_붙인_것으로_세지_않는다() {
        List<ReviewInsightEvalSamples> samples = List.of(new ReviewInsightEvalSamples("한국 사람인 걸 알자마자 퉁명스럽게",
                Set.of(ReviewTag.DISCRIMINATION), Set.of(ReviewTag.UNFRIENDLY)));

        EvalReport report = ReviewInsightEvalService.report(samples,
                List.of(EnumSet.of(ReviewTag.DISCRIMINATION, ReviewTag.UNFRIENDLY)), 0);

        assertThat(score(report, ReviewTag.UNFRIENDLY).wrongAdded()).isZero();
        assertThat(report.mistakes()).isEmpty();
    }

    @Test
    void 시험_문장의_정답은_서버_핵심_말_규칙과_어긋나지_않는다() {
        // 정답인데 핵심 말이 없으면 서버가 무조건 버리므로 시험 자체가 잘못된 것
        for (ReviewInsightEvalSamples s : ReviewInsightEvalSamples.ALL) {
            for (ReviewTag tag : s.expected()) {
                assertThat(tag.supportedBy(s.text())).as(s.text() + " / " + tag).isTrue();
            }
        }
        assertThat(ReviewInsightEvalSamples.ALL).hasSize(46);
    }

    @Test
    void 아무것도_아닌_시험_문장은_서버_규칙이_어떤_종류도_허용하지_않는다() {
        // AI가 잘못 붙여도 서버에서 버려지는지 (10-10 실제 표본에서 잘못 붙었던 유형 포함)
        for (ReviewInsightEvalSamples s : ReviewInsightEvalSamples.ALL) {
            if (!s.expected().isEmpty()) continue;
            for (ReviewTag tag : ReviewTag.values()) {
                assertThat(tag.supportedBy(s.text())).as(s.text() + " / " + tag).isFalse();
            }
        }
    }
}
