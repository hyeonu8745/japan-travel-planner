package com.example.japantravelplanner.poster;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 포스터 그리기는 한 장에 수십 초라 요청 스레드가 아니라 따로 돌린다 */
@Configuration
public class PosterConfig {

    /** 포스터 그리기 전용 스레드 풀 (동시에 그릴 장 수 = 설정 parallel, 대기 50장) */
    @Bean(name = "posterExecutor")
    public ThreadPoolTaskExecutor posterExecutor(PosterProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, props.getParallel()));
        executor.setMaxPoolSize(Math.max(1, props.getParallel()));
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("poster-");
        executor.initialize();
        return executor;
    }
}
