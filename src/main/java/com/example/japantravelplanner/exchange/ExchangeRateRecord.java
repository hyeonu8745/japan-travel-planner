package com.example.japantravelplanner.exchange;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 받아 온 엔화 환율 (하루 한 번). 공공 데이터(수출입은행)·공개 API 값이라 저장해도 된다.
 * 실패했을 때 마지막 값을 쓰려고 쌓아 둔다
 */
@Entity
@Table(name = "exchange_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRateRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1엔당 원 (100엔 = 951.05원이면 9.5105) */
    @Column(name = "krw_per_yen", nullable = false)
    private double krwPerYen;

    /** 환율 기준일 (수출입은행 고시일, Frankfurter 기준일) */
    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    public static ExchangeRateRecord of(double krwPerYen, LocalDate rateDate, String source, LocalDateTime fetchedAt) {
        ExchangeRateRecord r = new ExchangeRateRecord();
        r.krwPerYen = krwPerYen;
        r.rateDate = rateDate;
        r.source = source;
        r.fetchedAt = fetchedAt;
        return r;
    }

    public ExchangeRate toRate() {
        return new ExchangeRate(krwPerYen, rateDate, source);
    }
}
