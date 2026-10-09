package com.example.japantravelplanner.restaurant.entity;

/**
 * 타베로그 식당을 Google 장소와 맞춰본 결과. null이면 아직 확인하지 않은 식당이다.
 *
 * 캐글 타베로그 데이터는 몇 년 전 것이라 폐업한 식당이 섞여 있다 (10-09 표본 50곳 중 4곳은
 * 그 자리에 다른 가게가 있었다). 일정을 만들 때 고른 식당을 Google에서 찾아보고,
 * 찾지 못하거나 폐업이면 일정 후보에서 뺀다.
 */
public enum GoogleMatchStatus {

    /** 같은 가게를 찾았다 (place ID 저장) */
    MATCHED,

    /** 근처에 이름이 맞는 가게가 없다. 폐업·이름 변경일 가능성이 크다 (영어 이름 가게처럼 멀쩡한 곳이 섞일 수 있음) */
    NOT_FOUND,

    /** 같은 가게를 찾았지만 Google에 영업 종료(CLOSED_PERMANENTLY)로 나온다 */
    CLOSED;

    /** 끼니 후보에서 뺄 결과인지 */
    public boolean excludesFromMeals() {
        return this != MATCHED;
    }
}
