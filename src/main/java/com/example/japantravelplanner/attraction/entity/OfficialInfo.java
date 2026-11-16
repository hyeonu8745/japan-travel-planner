package com.example.japantravelplanner.attraction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광지 공식 운영 정보 (공식 사이트에서 확인한 운영 상태·예약·휴관일·시간·요금, 한국어 글자 그대로). 확인한 관광지만 있다.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OfficialInfo {

    @Column(name = "official_status", length = 20)
    private String status;

    @Column(name = "official_reservation", length = 100)
    private String reservation;

    @Column(name = "official_closed_days", length = 60)
    private String closedDays;

    @Column(name = "official_hours", length = 100)
    private String hours;

    @Column(name = "official_fee", length = 60)
    private String fee;

    @Column(name = "official_url", length = 200)
    private String url;
}
