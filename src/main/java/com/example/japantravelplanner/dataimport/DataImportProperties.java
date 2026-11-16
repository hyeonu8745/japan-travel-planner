package com.example.japantravelplanner.dataimport;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 확장 데이터 가져오기 위치 ({@code app.data-import.*}). 경로는 프로젝트 폴더 기준.
 * 데이터 파일과 백업은 저장소에 올리지 않는다 (data/, build/ 는 .gitignore).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.data-import")
public class DataImportProperties {

    /** 정리된 데이터 파일 폴더 (clean_*.json, attractions_tokyo.csv) */
    private String dataDir = "data/processed";

    /** 11-14 백업에서 빼 둔 값 폴더 (restaurant_keep.csv, attraction_keep.csv) */
    private String keepDir = "build/db-backup";

    /** 예전 도쿄 관광지 중 새 데이터에 없을 때 더할 최소 리뷰 수 (유명한 곳만) */
    private int minOldTokyoReviews = 1000;

    /** 한 번에 넣는 줄 수 */
    private int batchSize = 1000;
}
