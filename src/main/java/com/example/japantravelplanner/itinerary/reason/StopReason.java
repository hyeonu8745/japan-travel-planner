package com.example.japantravelplanner.itinerary.reason;

/**
 * 일정 항목을 고른 이유 하나 (화면의 칩)
 * @param label 화면에 그대로 보여줄 짧은 한국어 (예: "관심사: 야경", "타베로그 3.72", "숙소에서 걸어서 6분")
 */
public record StopReason(ReasonCode code, String label) {
}
