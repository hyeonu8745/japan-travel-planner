package com.example.japantravelplanner.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * places 프로필로 실행했을 때만 관광지 Google 장소 ID·평점을 받고 끝난다 (Google 요금이 드는 작업이라 평소엔 안 돈다).
 * 실행: {@code gradlew bootRun --args="--spring.profiles.active=places"}. 상한에 닿거나 실패하면 멈추고, 다시 실행하면 이어서 한다.
 */
@Component
@Profile("places")
@RequiredArgsConstructor
@Slf4j
public class PlaceLookupRunner implements CommandLineRunner {

    private final PlaceLookupJob job;
    private final ApplicationContext context;

    /** 받고, 건수를 남기고, 프로그램을 끝낸다 (실패하면 종료 코드 1) */
    @Override
    public void run(String... args) {
        int code = 0;
        try {
            PlaceLookupJob.Result r = job.run();
            log.info("[Places] 끝: 확인 {} (같은 곳 {} · 폐업 {} · 못 찾음 {} · 중복 {}) · 평점 {} · 예전 도쿄 평점 옮김 {} · "
                            + "호출 Pro {} · Enterprise {}{}",
                    r.checked(), r.matched(), r.closed(), r.notFound(), r.duplicates(), r.ratings(),
                    r.copiedRatings(), r.proCalls(), r.enterpriseCalls(),
                    r.stoppedAtLimit() ? " · 상한에 닿아 멈춤 (다시 실행하면 이어서)" : "");
            // IntelliJ 터미널에서 한글이 많은 마지막 줄이 안 보여서(11-16) 영어·숫자로 한 줄 더
            log.info("[Places] DONE checked={} matched={} closed={} notFound={} ratings={} pro={} enterprise={}{}",
                    r.checked(), r.matched(), r.closed(), r.notFound(), r.ratings(), r.proCalls(), r.enterpriseCalls(),
                    r.stoppedAtLimit() ? " STOPPED_AT_LIMIT" : "");
        } catch (RuntimeException e) {
            log.error("[Places] 중간에 멈춤 — 확인한 곳까지는 저장됐다. 다시 실행하면 이어서 한다", e);
            log.error("[Places] FAILED (saved so far is kept, run again to continue)");
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
