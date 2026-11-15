package com.example.japantravelplanner.trip.repository;

import com.example.japantravelplanner.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 여행 조회. 남의 여행을 못 보게 항상 사용자 id 와 같이 찾는다 (TripAccess).
 */
@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByUserId(Long userId);

    // 여행 id와 소유자 id가 모두 일치하는 여행만 조회 (타인의 여행 접근 차단)
    Optional<Trip> findByIdAndUserId(Long id, Long userId);
}