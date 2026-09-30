package com.example.japantravelplanner.trip.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 취향 대화에서 AI가 정리한 결과. 여행 하나에 하나.
 * AI는 매 대화마다 이 정리본 전체를 받아서, 갱신된 정리본 전체를 돌려준다.
 */
@Entity
@Table(name = "trip_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPreference {

    private static final double DEFAULT_FOOD_BUDGET_RATIO = 0.4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @ElementCollection
    @CollectionTable(name = "trip_preference_constraints",
            joinColumns = @JoinColumn(name = "trip_preference_id"))
    private List<PreferenceConstraint> constraints = new ArrayList<>();

    // 첫날 숙소 도착 시각, 마지막 날 출발 시각 (모르면 null → 기본 시간 규칙)
    private LocalTime arrivalTime;
    private LocalTime departureTime;

    // 1인 하루 예산 중 식비 비율 (기본 40%, 대화로 조정)
    @Column(nullable = false)
    private Double foodBudgetRatio = DEFAULT_FOOD_BUDGET_RATIO;

    // AI가 정리한 취향 요약 (다음 대화의 맥락으로 사용)
    @Column(length = 1000)
    private String summary;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public TripPreference(Trip trip) {
        this.trip = trip;
    }

    /** AI가 돌려준 정리본으로 통째로 교체 */
    public void update(List<PreferenceConstraint> constraints, LocalTime arrivalTime,
                       LocalTime departureTime, Double foodBudgetRatio, String summary) {
        this.constraints.clear();
        if (constraints != null) {
            this.constraints.addAll(constraints);
        }
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.foodBudgetRatio = foodBudgetRatio != null ? foodBudgetRatio : DEFAULT_FOOD_BUDGET_RATIO;
        this.summary = summary;
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}