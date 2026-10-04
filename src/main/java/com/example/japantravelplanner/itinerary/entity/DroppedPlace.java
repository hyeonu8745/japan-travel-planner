package com.example.japantravelplanner.itinerary.entity;

import com.example.japantravelplanner.itinerary.planner.DropReason;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 시간이 안 맞아 일정에서 빠진 관광지 (화면에 "빠진 장소"로 안내) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DroppedPlace {

    @Column(name = "place_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DropReason reason;
}