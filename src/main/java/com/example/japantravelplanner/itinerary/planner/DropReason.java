package com.example.japantravelplanner.itinerary.planner;

/** 후보였지만 일정에서 빠진 이유 */
public enum DropReason {
    TIME_SHORTAGE("시간이 부족해서"),
    NIGHT_WITHOUT_DINNER("저녁 시간대가 없어서 야경 일정을 뺐어요");

    private final String label;

    DropReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}