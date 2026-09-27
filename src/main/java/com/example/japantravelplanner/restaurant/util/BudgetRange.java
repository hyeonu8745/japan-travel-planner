package com.example.japantravelplanner.restaurant.util;

/**
 * 예산 범위 (엔 단위).
 * - min == null && max == null : 예산 정보 없음 (원본 "-")
 * - max == null (min만 있음)    : 상한 없음 (원본 "￥100,000～")
 */
public record BudgetRange(Integer min, Integer max) {

    public static final BudgetRange UNKNOWN = new BudgetRange(null, null);

    public boolean isUnknown() {
        return min == null && max == null;
    }
}