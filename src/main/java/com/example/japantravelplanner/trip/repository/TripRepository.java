package com.example.japantravelplanner.trip.repository;

import com.example.japantravelplanner.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByUserId(Long userId);

    // 여행 id와 소유자 id가 모두 일치하는 여행만 조회 (타인의 여행 접근 차단)
    Optional<Trip> findByIdAndUserId(Long id, Long userId);
}