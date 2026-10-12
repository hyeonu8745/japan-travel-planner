package com.example.japantravelplanner.trip.entity;

public enum TripStatus {
    CONDITIONS_SET,
    PREFERENCE_GATHERING,
    READY_TO_GENERATE,
    /** 일정을 AI로 만든 직후, 사용자가 [완성]을 누르기 전 (10-12). 이 상태에서도 일정 조회·수정 가능 */
    AWAITING_CONFIRMATION,
    GENERATED,
    EDITING
}