package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.zone.TripZone;

/** 점수와 지역이 붙은 관광지 후보 */
public record ScoredAttraction(Attraction attraction, TripZone zone, double score) {

    public double lat() {
        return attraction.getLocation().getY();
    }

    public double lng() {
        return attraction.getLocation().getX();
    }
}