package com.example.japantravelplanner.poster;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 여행 포스터 (10-12): 1장 = 일러스트 지도 포스터, 2장부터 = 상세 일정표. 모두 이미지 AI가 그린다 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.poster")
public class PosterProperties {

    private boolean enabled = true;
    private String model = "gpt-image-2";
    private String quality = "medium";
    /**
     * 모든 장의 크기 (10-14): A4 세로 비율(1:1.414)에 가깝게, 16의 배수. 인쇄할 때 위아래 여백이 생기지 않게
     * 지도·상세 모두 같은 크기로 (10-12에는 지도만 1152x2048이라 A4에 넣으면 좌우 여백이 생겼다)
     */
    private String pageSize = "1088x1536";
    /** 지도 포스터 한 장에 넣을 날 수. 넘으면 지도를 여러 장으로 (10-14: A4 한 크기라 3일 → 2일) */
    private int coverDaysPerPage = 2;
    /** 상세 일정표 한 장에 넣을 날 수 */
    private int daysPerDetailPage = 2;
    /** 지도 포스터에서 하루에 보여줄 장소 수 (일정 전체를 넣으려고 넉넉하게. 넘으면 식당·관광지 순으로 줄인다) */
    private int coverItemsPerDay = 8;
    /** 여행당 24시간 동안 만들 수 있는 포스터 묶음 수 */
    private int dailyLimit = 3;
    /** 이미지 한 장 기다리는 시간 */
    private int timeoutSeconds = 240;
    /** 동시에 그리는 장 수 */
    private int parallel = 2;
    /** 이 시간이 지나도 GENERATING이면(서버 재시작 등) 실패로 본다 */
    private int staleMinutes = 20;
    /** 이미지 파일을 둘 폴더 (저장소에 올리지 않는다) */
    private String storageDir = "storage/posters";
}
