package com.example.japantravelplanner.itinerary.naming;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 장소·식당 한국어 이름 만들기 설정 (app.korean-name) */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.korean-name")
public class KoreanNameProperties {

    /** false면 만들지 않는다 (화면은 원래 이름) */
    private boolean enabled = true;

    /** AI를 기다리는 최대 시간. 넘으면 이름 없이 진행 */
    private int timeoutSeconds = 15;

    /** 한 번에 보내는 이름 수 (넘으면 나눠서) */
    private int maxPerCall = 60;

    /** 실패한 이름은 이 시간 동안 다시 묻지 않는다 (일정 조회마다 AI를 부르지 않게) */
    private int retryAfterMinutes = 30;
}
