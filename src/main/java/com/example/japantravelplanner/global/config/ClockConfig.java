package com.example.japantravelplanner.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 서버 시계 하나 (11-08 리팩터링). 시간을 쓰는 서비스는 이 시계를 받아 자기 시간대로 바꿔 쓴다
 * (환율 = 한국, 날씨 = 일본). 테스트는 고정 시계를 생성자로 넣는다.
 * 예전에는 서비스마다 "Spring용 생성자 + 테스트용 생성자" 두 개를 두고 @Autowired 로 골랐다 (10-11 서버 기동 실패 사고).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
