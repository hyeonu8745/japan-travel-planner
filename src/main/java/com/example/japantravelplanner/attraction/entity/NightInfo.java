package com.example.japantravelplanner.attraction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 야경 명소의 밤 정보 (종류: 전망대·야간 개장·라이트업 등, 시간·요금·쉬는 날·주소). 야경 명소만 있다. */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NightInfo {

    @Column(name = "night_kind", length = 20)
    private String kind;

    @Column(name = "night_hours", length = 100)
    private String hours;

    @Column(name = "night_fee", length = 50)
    private String fee;

    @Column(name = "night_closed", length = 50)
    private String closed;

    @Column(name = "night_url", length = 200)
    private String url;
}
