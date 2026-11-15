package com.example.japantravelplanner.itinerary.repository;

import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 일정 항목 조회 (최신 버전의 항목인지 확인할 때).
 */
@Repository
public interface ItineraryStopRepository extends JpaRepository<ItineraryStop, Long> {
}