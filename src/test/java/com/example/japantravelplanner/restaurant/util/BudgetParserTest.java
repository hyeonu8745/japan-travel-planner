package com.example.japantravelplanner.restaurant.util;

import com.example.japantravelplanner.restaurant.util.BudgetParser;
import com.example.japantravelplanner.restaurant.util.BudgetRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetParserTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> min={1}, max={2}")
    @CsvSource(delimiter = '|', value = {
            "￥1,000～￥1,999     | 1000   | 1999",
            "￥10,000～￥14,999   | 10000  | 14999",
            "￥80,000～￥99,999   | 80000  | 99999",
            "～￥999             | 0      | 999",
            "￥100,000～         | 100000 |",
            "￥3,000〜￥3,999     | 3000   | 3999",   // 다른 물결표(U+301C)
    })
    void 범위_패턴을_파싱한다(String raw, Integer expectedMin, Integer expectedMax) {
        BudgetRange result = BudgetParser.parse(raw);

        assertThat(result.min()).isEqualTo(expectedMin);
        assertThat(result.max()).isEqualTo(expectedMax);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-", "   "})
    void 정보가_없으면_UNKNOWN을_반환한다(String raw) {
        assertThat(BudgetParser.parse(raw)).isEqualTo(BudgetRange.UNKNOWN);
    }

    @Test
    void 하한만_있으면_max는_null이다() {
        BudgetRange result = BudgetParser.parse("￥100,000～");

        assertThat(result.isUnknown()).isFalse();
        assertThat(result.min()).isEqualTo(100000);
        assertThat(result.max()).isNull();
    }
}