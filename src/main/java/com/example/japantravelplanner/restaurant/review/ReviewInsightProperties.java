package com.example.japantravelplanner.restaurant.review;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 식당 리뷰 분석 설정 (app.review-insight) */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.review-insight")
public class ReviewInsightProperties {

    /** false면 분석하지 않는다 (상세는 그대로) */
    private boolean enabled = true;

    /** 분석을 기다리는 최대 시간. 넘으면 분석 없이 상세를 돌려준다 */
    private int timeoutSeconds = 8;
}
