package com.example.japantravelplanner.itinerary.meal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.meal")
public class MealProperties {

    private double krwPerYen = 9.0;
    private double lunchShare = 0.35;
    private int minLunchYen = 1000;
    private int minDinnerYen = 2000;
    private int priorReviews = 50;
    private double preferBonus = 0.1;
    private double distancePenaltyPerKm = 0.1;
    private List<Integer> searchRadii = List.of(500, 1000, 1500);
    private List<Double> budgetRelaxSteps = List.of(1.2, 1.4);
    // 여행 중 같은 묶음(라멘류 등)이 이미 나왔을 때, 다시 나오는 횟수마다 깎는 점수
    private double repeatGroupPenalty = 0.15;
    // 반경 안에 후보가 이만큼 안 되면 더 넓은 반경까지 찾아서 고른다 (후보가 1곳뿐이면 선택지 없이 정해져 버림)
    private int minCandidates = 3;
}