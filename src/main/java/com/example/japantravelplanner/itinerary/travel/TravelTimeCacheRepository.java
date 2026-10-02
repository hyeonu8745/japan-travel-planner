package com.example.japantravelplanner.itinerary.travel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TravelTimeCacheRepository extends JpaRepository<TravelTimeCache, Long> {

    // 이번 장소들 사이의 캐시를 한 번에 조회 (만료되지 않은 것만)
    List<TravelTimeCache> findByModeAndOriginKeyInAndDestinationKeyInAndCreatedAtAfter(
            LegMode mode, Collection<String> originKeys, Collection<String> destinationKeys, LocalDateTime after);
}