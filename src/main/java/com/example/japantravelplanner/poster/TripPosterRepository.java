package com.example.japantravelplanner.poster;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripPosterRepository extends JpaRepository<TripPoster, Long> {

    Optional<TripPoster> findFirstByTripIdOrderByIdDesc(Long tripId);

    Optional<TripPoster> findByIdAndTripId(Long id, Long tripId);

    List<TripPoster> findByTripId(Long tripId);

    long countByTripIdAndCreatedAtAfter(Long tripId, LocalDateTime after);
}
