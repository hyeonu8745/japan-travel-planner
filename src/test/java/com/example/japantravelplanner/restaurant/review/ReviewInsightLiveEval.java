package com.example.japantravelplanner.restaurant.review;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.example.japantravelplanner.restaurant.dto.ReviewInsightsDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 실제 AI(OpenAI)로 시험 문장 정확도를 잰다 (11-18). 1차만 쓸 때와 다시 묻기까지 쓸 때를 비교한다.
 * 비용이 들어서 평소 테스트에서는 건너뛴다. 실행: 환경 변수 REVIEW_EVAL=1 (PC .env 의 OpenAI 키를 쓴다)
 *   PowerShell: $env:REVIEW_EVAL=1; .\gradlew test --tests "*ReviewInsightLiveEval*"; Remove-Item Env:REVIEW_EVAL
 * 결과: build/review_eval.txt (종류별 정밀도·재현율, 놓친 문장·잘못 붙은 문장)
 * 문장 5개를 리뷰 5개처럼 한 번에 보낸다 (실제 식당 상세와 같은 모양). gpt-4o-mini 기준 몇십 원.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "REVIEW_EVAL", matches = "1")
class ReviewInsightLiveEval {

    @Autowired
    ReviewInsightAnalyzer analyzer;

    private record Count(int[] tp, int[] fp, int[] fn) {
        Count() {
            this(new int[4], new int[4], new int[4]);
        }
    }

    @Test
    void 시험_문장_정확도() throws Exception {
        List<ReviewEvalSet.Case> cases = ReviewEvalSet.load();
        Count first = new Count(), result = new Count();
        List<String> notes = new ArrayList<>();
        int failedBatches = 0;
        for (int from = 0; from < cases.size(); from += 5) {
            List<ReviewEvalSet.Case> batch = cases.subList(from, Math.min(cases.size(), from + 5));
            List<ReviewDto> reviews = batch.stream()
                    .map(c -> new ReviewDto(null, 3, c.sentence(), false, null, "ko", "1달 전", null, null)).toList();
            ReviewInsightAnalyzer.Steps steps = analyzer.analyzeSteps(reviews);
            if (steps == null) {
                failedBatches++;
                continue;
            }
            tally(batch, steps.first(), first, null);
            tally(batch, steps.result(), result, notes);
        }
        StringBuilder sb = new StringBuilder("[리뷰 분석 실제 AI 평가] 문장 " + cases.size() + "개, 실패한 묶음 " + failedBatches + "\n");
        sb.append(table("1차만 (예전)", first)).append(table("다시 묻기까지 (지금)", result));
        sb.append("\n[지금 기준 놓침·잘못 붙음]\n");
        notes.forEach(n -> sb.append("  ").append(n).append('\n'));
        System.out.println(sb);
        Path out = Path.of("build", "review_eval.txt");
        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.print(sb);
        }
    }

    private static void tally(List<ReviewEvalSet.Case> batch, ReviewInsightsDto dto, Count count, List<String> notes) {
        Map<Integer, java.util.Set<ReviewTag>> got = new java.util.HashMap<>();
        dto.findings().forEach(f -> got.computeIfAbsent(f.reviewIndex(), k -> java.util.EnumSet.noneOf(ReviewTag.class))
                .add(ReviewTag.valueOf(f.tag())));
        for (int i = 0; i < batch.size(); i++) {
            ReviewEvalSet.Case c = batch.get(i);
            java.util.Set<ReviewTag> found = got.getOrDefault(i, java.util.EnumSet.noneOf(ReviewTag.class));
            for (ReviewTag tag : ReviewTag.values()) {
                boolean want = c.expected().contains(tag), have = found.contains(tag);
                if (want && have) count.tp[tag.ordinal()]++;
                if (!want && have) {
                    count.fp[tag.ordinal()]++;
                    if (notes != null) notes.add(tag + " 잘못 붙음: " + c.sentence());
                }
                if (want && !have) {
                    count.fn[tag.ordinal()]++;
                    if (notes != null) notes.add(tag + " 놓침: " + c.sentence());
                }
            }
        }
    }

    private static String table(String title, Count c) {
        StringBuilder sb = new StringBuilder("\n== " + title + " ==\n");
        int tp = 0, fp = 0, fn = 0;
        for (ReviewTag tag : ReviewTag.values()) {
            int t = c.tp[tag.ordinal()], f = c.fp[tag.ordinal()], n = c.fn[tag.ordinal()];
            tp += t;
            fp += f;
            fn += n;
            sb.append(String.format("%-16s 정밀도 %s · 재현율 %s (맞음 %d, 잘못 %d, 놓침 %d)%n", tag,
                    ratio(t, t + f), ratio(t, t + n), t, f, n));
        }
        sb.append(String.format("%-16s 정밀도 %s · 재현율 %s%n", "전체", ratio(tp, tp + fp), ratio(tp, tp + fn)));
        return sb.toString();
    }

    private static String ratio(int a, int b) {
        return b == 0 ? "-" : String.format("%.2f", (double) a / b);
    }
}
