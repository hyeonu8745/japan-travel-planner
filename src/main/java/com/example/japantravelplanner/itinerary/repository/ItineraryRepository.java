package com.example.japantravelplanner.itinerary.repository;

import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    // 가장 최근 버전
    Optional<Itinerary> findFirstByTripIdOrderByVersionNoDesc(Long tripId);

    // 변경 이력: 이 여행의 모든 버전 (최신이 먼저)
    List<Itinerary> findByTripIdOrderByVersionNoDesc(Long tripId);

    Optional<Itinerary> findByTripIdAndVersionNo(Long tripId, int versionNo);

    // 일정 생성 횟수 제한용 (AI로 생성한 버전만 센다. 식당 교체로 쌓인 버전은 세지 않는다)
    long countByTripIdAndSourceAndCreatedAtAfter(Long tripId, ItinerarySource source, LocalDateTime after);
}