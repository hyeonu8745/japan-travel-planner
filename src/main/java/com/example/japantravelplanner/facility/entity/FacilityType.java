package com.example.japantravelplanner.facility.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 편의 시설 종류. 데이터 파일의 type 값(code)과 짝을 이룬다.
 * 숙소 주변 안내(편의점·ATM·코인로커·환전·관광 안내소·온천)와 이동 제약 안내(엘리베이터·화장실)에 쓴다.
 */
public enum FacilityType {
    CONVENIENCE("convenience", "편의점"),
    ATM("atm", "ATM"),
    LOCKER("locker", "코인로커"),
    EXCHANGE("exchange", "환전소"),
    INFO("info", "관광 안내소"),
    PUBLIC_BATH("publicBath", "온천·목욕탕"),
    TOILETS("toilets", "화장실"),
    ELEVATORS("elevators", "엘리베이터"),
    ENTRANCES("entrances", "역 출입구"),
    PARKING("parking", "주차장"),
    BICYCLE("bicycle", "자전거 주차장");

    private final String code;
    private final String label;

    FacilityType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 데이터 파일의 type 값 */
    public String code() {
        return code;
    }

    /** 화면에 보일 한국어 이름 */
    public String label() {
        return label;
    }

    /** 데이터 파일의 type 값으로 찾기. 모르는 값이면 empty */
    public static Optional<FacilityType> ofCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }
}
