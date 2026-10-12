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
    /** gpt-image-2 계열만 사용자 지정 크기(긴 포스터)를 받는다 */
    private String model = "gpt-image-2";
    private String quality = "medium";
    /** 4일 이하 */
    private String coverSize = "1024x1536";
    /** 5일 이상이면 세로로 더 긴 크기 (gpt-image-2 계열만, 16의 배수) */
    private String longCoverSize = "1152x2048";
    private String detailSize = "1024x1536";
    /** 상세 일정표 한 장에 넣을 날 수 */
    private int daysPerDetailPage = 2;
    /** 지도 포스터에서 하루에 보여줄 장소 수 */
    private int coverItemsPerDay = 4;
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
