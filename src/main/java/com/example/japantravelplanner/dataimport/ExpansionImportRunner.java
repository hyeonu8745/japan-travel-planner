package com.example.japantravelplanner.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * import 프로필로 실행했을 때만 확장 데이터를 가져오고 끝난다. 평소 서버 실행에서는 만들어지지 않는다 (데이터를 지우는 작업이라).
 * 실행: {@code gradlew bootRun --args="--spring.profiles.active=import"}
 */
@Component
@Profile("import")
@RequiredArgsConstructor
@Slf4j
public class ExpansionImportRunner implements CommandLineRunner {

    private final ExpansionDataImporter importer;
    private final ApplicationContext context;

    /** 가져오고, 건수를 남기고, 프로그램을 끝낸다 (실패하면 종료 코드 1) */
    @Override
    public void run(String... args) {
        int code = 0;
        try {
            ExpansionDataImporter.Result r = importer.replaceAll();
            log.info("[Import] 완료: 브랜드 {} · 식당 {} · 체인점 {} · 관광지 {} · 편의 시설 {} · 식당 백업 값 {} · "
                            + "Google 장소 ID {} · 예전 도쿄 관광지 추가 {} · 관광지 백업 이름 {}",
                    r.brands(), r.restaurants(), r.chains(), r.attractions(), r.facilities(),
                    r.restaurantKeeps(), r.attractionPlaceIds(), r.oldTokyoAdded(), r.attractionKeeps());
            // IntelliJ 터미널에서 한글이 많은 마지막 줄이 안 보여서(11-16) 영어·숫자로 한 줄 더
            log.info("[Import] DONE brands={} restaurants={} chains={} attractions={} facilities={}",
                    r.brands(), r.restaurants(), r.chains(), r.attractions(), r.facilities());
        } catch (RuntimeException e) {
            log.error("[Import] 실패 — 아무것도 바뀌지 않았다", e);
            log.error("[Import] FAILED (nothing changed)");
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
