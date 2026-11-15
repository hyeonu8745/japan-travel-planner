package com.example.japantravelplanner.chat.dto;

import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.TripPreference;

import java.time.LocalTime;
import java.util.List;

/**
 * 취향 대화에서 정리된 조건 목록과 요약 (화면의 "정리된 취향").
 */
public record PreferenceResponseDto(
        List<ConstraintDto> constraints,
        LocalTime arrivalTime,
        LocalTime departureTime,
        Double foodBudgetRatio,
        String summary
) {

    public record ConstraintDto(ConstraintCategory category, String description) {
    }

    /** 정리본 엔티티 → 응답 */
    public static PreferenceResponseDto from(TripPreference preference) {
        List<ConstraintDto> constraints = preference.getConstraints().stream()
                .map(c -> new ConstraintDto(c.getCategory(), c.getDescription()))
                .toList();
        return new PreferenceResponseDto(constraints, preference.getArrivalTime(),
                preference.getDepartureTime(), preference.getFoodBudgetRatio(), preference.getSummary());
    }

    /** 아직 정리된 게 없을 때 (식비 비율 기본 0.4) */
    public static PreferenceResponseDto empty() {
        return new PreferenceResponseDto(List.of(), null, null, 0.4, null);
    }
}