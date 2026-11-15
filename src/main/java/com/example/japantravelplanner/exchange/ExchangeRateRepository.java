package com.example.japantravelplanner.exchange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 저장해 둔 환율 조회 (가장 최근 값).
 */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRateRecord, Long> {

    Optional<ExchangeRateRecord> findFirstByOrderByFetchedAtDesc();
}
