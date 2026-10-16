package com.example.japantravelplanner.itinerary.planner;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 오후 쉬는 시간 카페·디저트 (10-16, 10-13 사용자 아이디어).
 * 점심과 저녁 사이, 앞 장소를 떠나는 시각이 earliest~latest 사이인 자리 중 preferred에 가장 가까운 곳에 한 번 넣는다.
 * 넣었을 때 저녁이 늦어지거나 하루가 넘치면 넣지 않는다 (관광지를 빼면서까지 넣지 않는다).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cafe")
public class CafeProperties {

    private boolean enabled = true;
    private int stayMinutes = 40;
    /** 분 (14:00, 16:30, 15:00) */
    private int earliestMinutes = 14 * 60;
    private int latestMinutes = 16 * 60 + 30;
    private int preferredMinutes = 15 * 60;
    private List<Integer> searchRadii = new ArrayList<>(List.of(300, 600, 1000));
    /** 1인 예산 상한 (엔). 디저트 코스·호텔 애프터눈 티처럼 비싼 곳은 쉬는 시간으로 넣지 않는다 */
    private int maxYen = 2500;
    /** 점수 = 평점 − 거리(km) × 이 값 */
    private double distancePenalty = 0.5;
}
