package com.example.japantravelplanner.itinerary.entity;

/**
 * 일정 한 버전이 만들어진 방식.
 * 하루 생성 횟수 제한(AI 비용 대책)은 GENERATED만 센다. 식당 교체는 AI를 부르지 않으므로 횟수에 넣지 않는다.
 */
public enum ItinerarySource {
    /** AI(Terra)로 새로 생성 */
    GENERATED,
    /** 사용자가 고침 (식당 교체 등). AI 비용 없음 */
    EDITED,
    /** 이전 버전을 그대로 되돌려서 새 버전으로 만듦. AI 비용 없음 */
    RESTORED
}