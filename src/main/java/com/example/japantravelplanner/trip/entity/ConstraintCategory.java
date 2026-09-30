package com.example.japantravelplanner.trip.entity;

/** 대화에서 파악한 조건의 종류. DB로 거를 수 있는 것과 AI가 참고만 하는 것을 구분하기 위함 */
public enum ConstraintCategory {
    FOOD_PREFER,    // 먹고 싶은 음식 (장르 검색에 사용)
    FOOD_AVOID,     // 피하고 싶은 음식 (장르 제외에 사용)
    ATMOSPHERE,     // 분위기 (조용한 곳 등)
    MOBILITY,       // 이동·체력 (계단 적게 등)
    SMOKING,        // 흡연 (이자카야·바 제외 등)
    SCHEDULE,       // 일정 (특정 날 특정 지역 등)
    ETC             // 그 외
}