package com.example.japantravelplanner.chat.dto;

import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.TripPreference;

import java.time.LocalTime;
import java.util.List;

public record PreferenceResponseDto(
        List<ConstraintDto> constraints,
        LocalTime arrivalTime,
        LocalTime departureTime,
        Double foodBudgetRatio,
        String summary
) {

    public record ConstraintDto(ConstraintCategory category, String description) {
    }

    public static PreferenceResponseDto from(TripPreference preference) {
        List<ConstraintDto> constraints = preference.getConstraints().stream()
                .map(c -> new ConstraintDto(c.getCategory(), c.getDescription()))
                .toList();
        return new PreferenceResponseDto(constraints, preference.getArrivalTime(),
                preference.getDepartureTime(), preference.getFoodBudgetRatio(), preference.getSummary());
    }

    public static PreferenceResponseDto empty() {
        return new PreferenceResponseDto(List.of(), null, null, 0.4, null);
    }
}