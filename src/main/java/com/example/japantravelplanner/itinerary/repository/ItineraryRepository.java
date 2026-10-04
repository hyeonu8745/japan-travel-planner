package com.example.japantravelplanner.itinerary.repository;

import com.example.japantravelplanner.itinerary.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    // 가장 최근 버전
    Optional<Itinerary> findFirstByTripIdOrderByVersionNoDesc(Long tripId);

    // 일정 생성 횟수 제한용
    long countByTripIdAndCreatedAtAfter(Long tripId, LocalDateTime after);
}