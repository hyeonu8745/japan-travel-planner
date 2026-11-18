package com.example.japantravelplanner.restaurant.review;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시험 문장으로 서버 규칙만 잰다 (AI 없이, 11-18).
 * - 훑기 재현율: 정답 종류가 있는 문장을 규칙이 그 종류로 골라내는 비율 → 높을수록 AI 다시 묻기로 살릴 수 있다
 * - 잘못 걸림: 정답이 아닌 종류에 규칙이 걸리는 비율 → 다시 묻기에서 AI가 걸러야 하는 양 (AI가 그 종류를 잘못 붙였을 때 서버가 못 막는 양)
 * 실제 AI까지 넣은 정확도는 ReviewInsightLiveEval (OpenAI 키 필요, 수동 실행)
 */
class ReviewRulesEvalTest {

    @Test
    void 시험_문장_규칙_재현율과_잘못_걸림() {
        List<ReviewEvalSet.Case> cases = ReviewEvalSet.load();
        StringBuilder report = new StringBuilder("\n[규칙 평가] 문장 " + cases.size() + "개\n");
        List<String> missed = new ArrayList<>();
        int allPositives = 0, allFlagged = 0;
        for (ReviewTag tag : ReviewTag.values()) {
            int positives = 0, flagged = 0, negatives = 0, wrong = 0;
            for (ReviewEvalSet.Case c : cases) {
                boolean hit = ReviewSuspects.suspicious(tag, c.sentence());
                if (c.expected().contains(tag)) {
                    positives++;
                    if (hit) flagged++; else missed.add(tag + " 놓침: " + c.sentence());
                } else {
                    negatives++;
                    if (hit) {
                        wrong++;
                        missed.add(tag + " 잘못 걸림: " + c.sentence());
                    }
                }
            }
            allPositives += positives;
            allFlagged += flagged;
            report.append(String.format("%-16s 훑기 재현율 %d/%d (%.2f) · 잘못 걸림 %d/%d (%.2f)%n", tag, flagged, positives,
                    (double) flagged / positives, wrong, negatives, (double) wrong / negatives));
        }
        report.append(String.format("전체 훑기 재현율 %d/%d (%.2f)%n", allFlagged, allPositives, (double) allFlagged / allPositives));
        missed.forEach(m -> report.append("  ").append(m).append('\n'));
        System.out.println(report);

        assertThat(cases).hasSizeGreaterThanOrEqualTo(150);
        // 규칙은 마지막 확인에도 쓰여서, 여기서 놓친 문장은 AI가 맞게 찾아도 버려진다.
        // 규칙 목록이 줄어들어 훑기 재현율이 떨어지면 알 수 있게 (11-18 측정값보다 조금 낮게)
        assertThat((double) allFlagged / allPositives).isGreaterThanOrEqualTo(0.95);   // 11-18 보강 후 0.99
    }

    @Test
    void 해당_없음_문장은_종류_하나에_잘못_걸려도_서버가_대부분_막는다() {
        List<ReviewEvalSet.Case> none = ReviewEvalSet.load().stream().filter(c -> c.expected().isEmpty()).toList();
        long leaked = none.stream()
                .filter(c -> java.util.Arrays.stream(ReviewTag.values()).anyMatch(t -> ReviewSuspects.suspicious(t, c.sentence())))
                .count();
        // 규칙에 걸리는 "해당 없음" 문장은 AI(1차·다시 묻기)가 판단한다. 그 양이 너무 많아지면 규칙이 넓어진 것
        assertThat((double) leaked / none.size()).isLessThanOrEqualTo(0.4);
    }
}
