package com.example.japantravelplanner.restaurant.controller;

import com.example.japantravelplanner.restaurant.review.ReviewInsightEvalService;
import com.example.japantravelplanner.restaurant.review.ReviewInsightEvalService.EvalReport;
import com.example.japantravelplanner.restaurant.review.ReviewInsightEvalService.SampleRow;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 리뷰 분석 정확도 확인 (개발용, 배포 전 제거 대상 — 보안 강화 1-2에서 dev 프로필로 묶는다).
 * eval은 OpenAI만, sample은 Google 상세(+필요하면 매칭 검색)와 OpenAI를 식당 수만큼 부른다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/review-insight")
public class ReviewInsightDevController {

    private final ReviewInsightEvalService evalService;

    /** A. 정답을 단 시험 문장 34개로 종류별 정밀도·재현율 (AI 7번) */
    @GetMapping("/eval")
    public EvalReport eval() {
        return evalService.eval();
    }

    /** B. 리뷰 많은 식당의 실제 리뷰와 분석 결과 (Google 상세 limit건, 최대 30) */
    @GetMapping("/sample")
    public List<SampleRow> sample(@RequestParam(defaultValue = "20") int limit) {
        return evalService.sample(limit);
    }
}
