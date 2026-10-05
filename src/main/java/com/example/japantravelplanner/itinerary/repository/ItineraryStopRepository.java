package com.example.japantravelplanner.itinerary.repository;

import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItineraryStopRepository extends JpaRepository<ItineraryStop, Long> {
}