package com.example.japantravelplanner.trip.repository;

import com.example.japantravelplanner.trip.entity.TripPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripPreferenceRepository extends JpaRepository<TripPreference, Long> {

    Optional<TripPreference> findByTripId(Long tripId);

    // 여행을 지울 때 파악한 취향도 함께 지운다
    void deleteByTripId(Long tripId);
}