package com.example.japantravelplanner.exchange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRateRecord, Long> {

    Optional<ExchangeRateRecord> findFirstByOrderByFetchedAtDesc();
}
