package com.example.japantravelplanner.global.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google API 설정 ({@code app.google.*}): 키(.env), 시간 제한, 장소 상세 언어, 사진 크기.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {

    private String apiKey;
    private int timeoutSeconds = 10;

    // Places 상세(소개·리뷰·영업시간)를 받을 언어. 리뷰는 이 언어로 번역되어 오고 원문도 함께 온다
    private String placesLanguage = "ko";

    // 사진 주소를 만들 때 가로 최대 픽셀 (요청 값이 없거나 범위를 벗어나면 이 값)
    private int photoMaxWidthPx = 800;
}