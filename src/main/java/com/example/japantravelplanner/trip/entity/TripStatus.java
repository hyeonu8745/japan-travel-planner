package com.example.japantravelplanner.trip.entity;

/**
 * 여행 진행 상태.
 * CONDITIONS_SET(조건 입력) → PREFERENCE_GATHERING(취향 대화 중) → READY_TO_GENERATE(만들 준비) → AWAITING_CONFIRMATION(만든 직후, 완성 전)
 * → GENERATED(완성) → EDITING(완성 뒤 고침).
 */
public enum TripStatus {
    CONDITIONS_SET,
    PREFERENCE_GATHERING,
    READY_TO_GENERATE,
    /** 일정을 AI로 만든 직후, 사용자가 [완성]을 누르기 전. 이 상태에서도 일정 조회·수정 가능 */
    AWAITING_CONFIRMATION,
    GENERATED,
    EDITING
}