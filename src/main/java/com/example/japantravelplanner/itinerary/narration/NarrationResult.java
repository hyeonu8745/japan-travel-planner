package com.example.japantravelplanner.itinerary.narration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** AI가 돌려준 날짜별 설명 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NarrationResult(List<DayText> days) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DayText(int day, String description) {
    }
}