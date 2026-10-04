package com.example.japantravelplanner.itinerary.narration;

import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.entity.TravelPace;

import java.util.List;

/** 설명을 쓸 때 AI에게 알려줄 여행 정보 (비밀·개인정보 없이 취향만) */
public record TripBrief(Integer travelers,
                        TravelPace pace,
                        List<Interest> interests,
                        List<PreferenceConstraint> constraints) {
}