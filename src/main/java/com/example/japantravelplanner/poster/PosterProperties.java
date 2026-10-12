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
    /** 지도 한 장에 이틀 이하이고 하루 4곳 이하일 때 */
    private String coverSize = "1024x1536";
    /** 그보다 많으면 세로로 더 긴 크기 (gpt-image-2 계열만, 16의 배수) */
    private String longCoverSize = "1152x2048";
    /** 지도 포스터 한 장에 넣을 날 수. 넘으면 지도를 여러 장으로 (10-12: 일정 전체를 지도에 넣기로) */
    private int coverDaysPerPage = 3;
    private String detailSize = "1024x1536";
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
