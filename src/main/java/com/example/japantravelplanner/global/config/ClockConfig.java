package com.example.japantravelplanner.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 서버 시계 하나. 시간을 쓰는 서비스는 이 시계를 받아 자기 시간대로 바꿔 쓴다
 * (환율 = 한국, 날씨 = 일본). 테스트는 고정 시계를 생성자로 넣는다.
 * 서비스 생성자는 하나만 둔다 (생성자가 두 개면 Spring 이 고를 생성자를 헷갈려 서버가 켜지지 않을 수 있다).
 */
@Configuration
public class ClockConfig {

    /** 서버 시계 (UTC). 서비스마다 필요한 시간대로 바꿔 쓴다 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
