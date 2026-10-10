package com.example.japantravelplanner.itinerary.reason;

/** 일정 항목을 고른 이유의 종류 (화면에서 칩 색·아이콘을 고를 때 쓴다) */
public enum ReasonCode {
    INTEREST,      // 관심사와 맞는 종류의 관광지
    HIGH_RATING,   // 평점이 높음 (관광지: Google, 식당: 타베로그)
    MANY_REVIEWS,  // 리뷰가 아주 많은 관광지
    FOOD_PREFER,   // 대화에서 말한 좋아하는 음식
    NEAR           // 앞 장소(첫 항목은 숙소)에서 걸어서 가까움
}
