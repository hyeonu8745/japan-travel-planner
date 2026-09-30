package com.example.japantravelplanner.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대화에서 파악한 조건 하나. 예: (FOOD_AVOID, "날생선(회) 제외") */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PreferenceConstraint {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConstraintCategory category;

    @Column(nullable = false, length = 300)
    private String description;
}