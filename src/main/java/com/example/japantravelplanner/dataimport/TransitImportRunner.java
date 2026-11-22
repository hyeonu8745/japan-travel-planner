package com.example.japantravelplanner.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * transit 프로필로 실행했을 때만 대중교통 노선을 가져오고 끝난다 (11-22).
 * 실행: {@code gradlew bootRun --args="--spring.profiles.active=transit"}
 */
@Component
@Profile("transit")
@RequiredArgsConstructor
@Slf4j
public class TransitImportRunner implements CommandLineRunner {

    private final TransitImporter importer;
    private final ApplicationContext context;

    /** 가져오고, 건수를 남기고, 프로그램을 끝낸다 (실패하면 종료 코드 1) */
    @Override
    public void run(String... args) {
        int code = 0;
        try {
            TransitImporter.Result r = importer.replaceAll();
            log.info("[Transit] DONE stops={} routes={}", r.stops(), r.routes());
        } catch (RuntimeException e) {
            log.error("[Transit] FAILED (nothing changed)", e);
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
