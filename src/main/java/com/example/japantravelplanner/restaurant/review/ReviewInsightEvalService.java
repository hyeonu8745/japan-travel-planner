package com.example.japantravelplanner.restaurant.review;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto;
import com.example.japantravelplanner.restaurant.dto.ReviewInsightsDto;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.restaurant.service.RestaurantDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/**
 * 리뷰 분석 정확도 확인 (개발용, 10-10). 결과는 응답으로만 보여주고 저장하지 않는다.
 *  A. eval: 정답을 단 시험 문장으로 종류별 정밀도(잘못 붙이지 않음)·재현율(놓치지 않음)
 *  B. sample: 리뷰 많은 식당의 실제 상세를 불러 리뷰와 분석 결과를 나란히 (사람이 판정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewInsightEvalService {

    /** 실제 상세처럼 한 번에 리뷰 5개씩 AI에 보낸다 */
    static final int BATCH = 5;
    static final int MAX_SAMPLE = 30;

    private final ReviewInsightAnalyzer analyzer;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantDetailService restaurantDetailService;

    // ===================== A. 시험 문장 =====================

    public EvalReport eval() {
        List<ReviewInsightEvalSamples> samples = ReviewInsightEvalSamples.ALL;
        List<Set<ReviewTag>> predicted = new ArrayList<>();
        int failedBatches = 0;

        for (int start = 0; start < samples.size(); start += BATCH) {
            List<ReviewInsightEvalSamples> batch = samples.subList(start, Math.min(start + BATCH, samples.size()));
            List<ReviewDto> reviews = batch.stream()
                    .map(s -> new ReviewDto(null, null, s.text(), false, null, "ko", null, null, null))
                    .toList();
            List<Set<ReviewTag>> tags = new ArrayList<>();
            batch.forEach(s -> tags.add(EnumSet.noneOf(ReviewTag.class)));

            ReviewInsightsDto result = analyzer.analyze(reviews);
            if (result == null) {
                failedBatches++;
            } else {
                result.findings().forEach(f -> tags.get(f.reviewIndex()).add(ReviewTag.valueOf(f.tag())));
            }
            predicted.addAll(tags);
        }
        return report(samples, predicted, failedBatches);
    }

    static EvalReport report(List<ReviewInsightEvalSamples> samples, List<Set<ReviewTag>> predicted, int failedBatches) {
        Map<ReviewTag, int[]> counts = new EnumMap<>(ReviewTag.class);   // [tp, fp, fn]
        for (ReviewTag tag : ReviewTag.values()) counts.put(tag, new int[3]);
        List<Mistake> mistakes = new ArrayList<>();

        for (int i = 0; i < samples.size(); i++) {
            ReviewInsightEvalSamples s = samples.get(i);
            Set<ReviewTag> got = predicted.get(i);
            List<String> wrong = new ArrayList<>();
            for (ReviewTag tag : ReviewTag.values()) {
                boolean want = s.expected().contains(tag);
                boolean has = got.contains(tag);
                if (want && has) counts.get(tag)[0]++;
                if (!want && has && !s.allowed().contains(tag)) {
                    counts.get(tag)[1]++;
                    wrong.add("잘못 붙임: " + tag.label());
                }
                if (want && !has) {
                    counts.get(tag)[2]++;
                    wrong.add("놓침: " + tag.label());
                }
            }
            if (!wrong.isEmpty()) {
                mistakes.add(new Mistake(s.text(), s.expected().stream().map(ReviewTag::label).toList(),
                        got.stream().map(ReviewTag::label).toList(), wrong));
            }
        }

        List<TagScore> scores = new ArrayList<>();
        for (ReviewTag tag : ReviewTag.values()) {
            int[] c = counts.get(tag);
            scores.add(new TagScore(tag.name(), tag.label(), c[0], c[1], c[2],
                    ratio(c[0], c[0] + c[1]), ratio(c[0], c[0] + c[2])));
        }
        return new EvalReport(samples.size(), failedBatches, scores, mistakes);
    }

    private static Double ratio(int a, int b) {
        return b == 0 ? null : Math.round(1000.0 * a / b) / 1000.0;
    }

    /**
     * @param precision 붙인 것 중 맞은 비율 (잘못 붙이지 않음). 붙인 게 없으면 null
     * @param recall    있어야 할 것 중 찾은 비율 (놓치지 않음)
     */
    public record TagScore(String tag, String label, int correct, int wrongAdded, int missed,
                           Double precision, Double recall) {
    }

    public record Mistake(String text, List<String> expected, List<String> predicted, List<String> problems) {
    }

    public record EvalReport(int samples, int failedBatches, List<TagScore> scores, List<Mistake> mistakes) {
    }

    // ===================== B. 실제 식당 표본 =====================

    public List<SampleRow> sample(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_SAMPLE));
        List<Restaurant> restaurants = restaurantRepository.findReviewSample(GoogleMatchStatus.MATCHED,
                PageRequest.of(0, size));

        List<SampleRow> rows = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SampleRow>> futures = restaurants.stream()
                    .map(r -> executor.submit(() -> sampleRow(r)))
                    .toList();
            for (Future<SampleRow> f : futures) {
                try {
                    rows.add(f.get());
                } catch (Exception e) {
                    log.warn("[ReviewInsightEval] 표본 실패: {}", e.getClass().getSimpleName());
                }
            }
        }
        return rows;
    }

    private SampleRow sampleRow(Restaurant r) {
        try {
            RestaurantDetailResponseDto detail = restaurantDetailService.details(r.getId());
            if (detail.google() == null) {
                return new SampleRow(r.getId(), r.getName(), detail.googleStatus().name(), null, List.of(), List.of(), null);
            }
            List<ReviewDto> reviews = detail.google().reviews();
            List<SampleReview> shown = IntStream.range(0, reviews.size())
                    .mapToObj(i -> new SampleReview(i, reviews.get(i).translated(), reviews.get(i).originalLanguage(),
                            reviews.get(i).rating(), reviews.get(i).text()))
                    .toList();
            ReviewInsightsDto insights = detail.google().reviewInsights();
            return new SampleRow(r.getId(), r.getName(), detail.googleStatus().name(),
                    insights == null ? null : insights.analyzedReviewCount(), shown,
                    insights == null ? List.of() : insights.findings(), insights == null ? "분석 실패 또는 리뷰 없음" : null);
        } catch (RuntimeException e) {
            return new SampleRow(r.getId(), r.getName(), null, null, List.of(), List.of(), e.getMessage());
        }
    }

    public record SampleReview(int index, boolean translated, String originalLanguage, Integer rating, String text) {
    }

    public record SampleRow(Long restaurantId, String name, String googleStatus, Integer analyzedReviewCount,
                            List<SampleReview> reviews, List<ReviewInsightsDto.Finding> findings, String note) {
    }
}
