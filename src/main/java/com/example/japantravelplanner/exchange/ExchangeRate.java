package com.example.japantravelplanner.exchange;

import java.time.LocalDate;

/**
 * 일정 계산에 쓴 환율.
 *
 * @param krwPerYen 1엔당 원
 * @param date      기준일 (고정값이면 null)
 * @param source    KOREAEXIM(한국수출입은행) / FRANKFURTER / FIXED(설정 고정값)
 */
public record ExchangeRate(double krwPerYen, LocalDate date, String source) {

    public static final String KOREAEXIM = "KOREAEXIM";
    public static final String FRANKFURTER = "FRANKFURTER";
    public static final String FIXED = "FIXED";
}
