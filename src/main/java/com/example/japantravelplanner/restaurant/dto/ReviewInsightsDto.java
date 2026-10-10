package com.example.japantravelplanner.restaurant.dto;

import java.util.List;

/**
 * Google 리뷰(최대 5개)를 AI가 읽고 찾은 경험 표시. 저장하지 않고 상세를 열 때마다 만든다.
 *
 * 리뷰 몇 개로 가게를 단정하지 않는다: 화면에는 "이런 경험을 적은 리뷰가 있어요" 수준으로만 보여주고 notice를 함께 표시한다.
 *
 * @param findings 리뷰별로 찾은 경험 (reviewIndex는 google.reviews 배열의 위치, 0부터)
 * @param counts   종류별 리뷰 수 (정해진 순서, 0인 종류는 빠짐)
 * @param notice   화면에 함께 보여줄 안내 문장
 * @param analyzedReviewCount 분석한 리뷰 수 = 한국어로 쓴 리뷰만 (번역된 리뷰는 빼고 읽는다). 0이면 한국인이 쓴 리뷰가 없어 분석하지 않은 것
 */
public record ReviewInsightsDto(List<Finding> findings, List<TagCount> counts, String notice,
                                int analyzedReviewCount) {

    /**
     * @param tag      DISCRIMINATION / UNFRIENDLY / HYGIENE / KOREAN_FRIENDLY
     * @param label    한국어 이름
     * @param caution  주의가 필요한 경험이면 true (좋은 점은 false)
     * @param evidence 리뷰에서 그대로 옮긴 짧은 부분 (서버가 리뷰 원문에 있는지 확인한 것만)
     */
    public record Finding(int reviewIndex, String tag, String label, boolean caution, String evidence) {
    }

    public record TagCount(String tag, String label, boolean caution, int count) {
    }
}
