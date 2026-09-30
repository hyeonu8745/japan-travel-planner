package com.example.japantravelplanner.trip.repository;

import com.example.japantravelplanner.trip.entity.TripPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripPreferenceRepository extends JpaRepository<TripPreference, Long> {

    Optional<TripPreference> findByTripId(Long tripId);
}